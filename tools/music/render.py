#!/usr/bin/env python3
"""Compose original piano-rock scores, render 3 cycles, keep the settled third loop.
Requires numpy, libfluidsynth 2.x, FFmpeg, GeneralUser GS 2.0.3 (see docs/MUSIC.md).
No network access or external compositions are used by this script.
"""
import argparse, ctypes as C, hashlib, json, math, os, random, struct, subprocess, wave
from concurrent.futures import ProcessPoolExecutor
from functools import partial
from pathlib import Path
import numpy as np
from battle_arranger import compose_battle
from battle_scores import SCORES

ROOT = Path(__file__).resolve().parents[2]
RATE = 44100
SCALE = [0, 2, 3, 5, 7, 8, 10]
CHORDS = [[8, 12, 15], [10, 14, 17], [0, 3, 7], [0, 3, 7]]
SF_SHA = '9575028c7a1f589f5770fccc8cff2734566af40cd26ed836944e9a5152688cfe'


def degree(n):
    return n // 7 * 12 + SCALE[n % 7]


def compose(t):
    if t['id'] in SCORES:
        return compose_battle(t)
    bars = int(math.floor(30 * t['bpm'] / (60 * t['beats'] * 4) + .5)) * 4
    beats, voice, root = t['beats'], t['voice'], t['root']
    gentle = voice in ('shop', 'ending')
    # Central melody register stays recognizably piano, even for the enormous low gods.
    lead_root = 60 + root % 12
    bass_root = 28 + (root - 28) % 12
    guitar_root = bass_root + 12
    rng = random.Random(t['id'])
    events = []
    def note(ch, pos, dur, pitch, velocity):
        if not 0 <= pos < bars * beats:
            return
        pitch = max(21, min(108, int(pitch)))
        velocity = max(1, min(127, int(velocity + rng.randint(-3, 3))))
        events.append((pos, 1, ch, pitch, velocity))
        events.append((min(pos + dur, bars * beats - .005), 0, ch, pitch, 0))
    for bar in range(bars):
        start, chord = bar * beats, CHORDS[bar % 4]
        # 4-bar harmonic phrases: motif, answer, a lighter bridge, climax, turnaround.
        block = bar // 4
        bridge = bars >= 24 and block == 4
        climax = bar >= bars - 8
        phrase = t['phrase_b'] if block % 2 or climax else t['phrase_a']
        intensity = .64 if bridge else 1.08 if climax else .9
        if voice == 'ending':
            intensity = .58 + .42 * bar / (bars - 1)
        elif voice == 'shop':
            intensity = .68
        # Piano right hand: a singable quarter/eighth-note motif, with phrase-ending breaths.
        for beat in range(beats):
            k = (bar % 4 * 4 + beat) % 16
            motif = phrase[k]
            register = 12 if (voice in ('spark', 'ice') and bar % 4 in (1, 3)) else 0
            if voice == 'dual' and bar % 2:
                register = -12
            if climax and voice in ('fire', 'horn', 'raven'):
                register += 12
            n = degree(motif) + register
            # The first and third beats land on the ACTUAL major VI/VII or minor tonic.
            if beat % 2 == 0:
                n = min((x for x in range(n-6, n+7) if x % 12 in [c % 12 for c in chord]), key=lambda x: abs(x-n))
            pos = start + beat
            if t['id'] == 'tyr' and beat == 2 and bar % 4 == 3:
                continue  # One conspicuous missing beat: the sacrificed hand.
            if t['id'] == 'fenrir' and bar % 4 == 3 and beat == beats-1:
                continue  # Breath before the chain-breaking downbeat.
            short = voice in ('ice', 'spark', 'pluck', 'trick')
            duration = .42 if short else .82
            if voice in ('wind', 'ending') and beat == beats-1:
                duration = .94
            note(0, pos, duration, lead_root+n, (94 if not gentle else 82)*intensity)
            if climax and not gentle and beat % 2 == 0:
                note(0, pos+.007, duration*.85, lead_root+n-12, 54*intensity)
            # Answers decorate the melody, never an unbroken same-density scale exercise.
            add = not bridge and (beat % 2 == 1 or voice in ('hunt', 'fire'))
            if gentle:
                add = beat == 1 or (voice == 'shop' and beat == 3 and bar % 2 == 0)
            if add and not (bar % 4 == 3 and beat == beats-1):
                next_n = degree(phrase[(k+1) % 16]) + register
                passing = n + (2 if next_n > n else -2 if next_n < n else 7)
                # Scale quantization keeps the ornamental passing tones in the key.
                passing = min((x for x in range(passing-2,passing+3) if x % 12 in SCALE), key=lambda x: abs(x-passing))
                note(0, pos+.5, .36 if short else .43, lead_root+passing, 70*intensity)
            if voice == 'fire' and climax and beat == beats-1 and bar % 4 != 3:
                for j in range(1,4):
                    note(0, pos+j/4, .19, lead_root+degree(motif+j), 78)
            # Raven/memory voices answer at distinct positions and stereo locations.
            if voice in ('echo','raven') and beat in (1,3):
                delay = .5 if voice == 'echo' else .25
                note(1, pos+delay, .55, lead_root-12+degree(phrase[(k-2) % 16]), 55*intensity)
        # Left hand: flowing inversions below the tune. Triplet waves only for the sea.
        count = beats * (3 if voice == 'sea' else 2)
        arp_order = [0,1,2,1,2,1] if voice == 'sea' else [0,2,1,2]
        for j in range(count):
            pos = start+j*beats/count
            if voice == 'stone' and j % 3 == 2:
                continue
            n = chord[arp_order[j % len(arp_order)]]
            note(1, pos, .36 if not gentle else .65, lead_root-24+n+(12 if j%8>=4 else 0), (41 if not bridge else 52)*intensity)
        # Double-tracked guitars: power chords, palm-muted eighths, open phrase accents.
        attacks = [i*.5 for i in range(beats*2)]
        if voice in ('wind', 'dual', 'ending') or bridge:
            attacks = [0, beats/2]
        if voice == 'shop':
            attacks = [.5, 1.5, 2.5, 3.5]
        if voice == 'stone':
            attacks = [0, .75, 1.5, 3]  # Stone heart, three blows against a rock backbeat.
        if voice == 'trick':
            attacks = [0,.75,1.5,2.5] if beats == 3 else [.25,1,1.75,3]
        for j, off in enumerate(attacks):
            if off >= beats or (t['id'] == 'fenrir' and bar%4==3 and off >= beats-1):
                continue
            if t['id'] == 'tyr' and bar%4==3 and 2 <= off < 3:
                continue
            length = min(beats-off-.03, 1.55 if len(attacks)<=2 else .28 if j%2 else .42)
            intervals = chord if gentle else [chord[0],chord[0]+7,chord[0]+12]
            for ch in (2,3):
                for n in intervals:
                    note(ch,start+off+(ch-2)*.013,length,guitar_root+n, (67 if j%2==0 else 53)*intensity)
        # Electric bass is a played line with fifths/octaves and a final-beat pickup.
        for j in range(beats*2):
            if voice == 'ending' and j % 2:
                continue
            if t['id']=='tyr' and bar%4==3 and j in (4,5):
                continue
            n = chord[0] + ([0,0,12,7][j%4] if not gentle else [0,7][j%2])
            if j == beats*2-1 and bar%4==3:
                n = 7  # E -> F in A minor on the next loop/phrase.
            note(4,start+j*.5,.42 if not gentle else .82,bass_root+n,77*intensity)
        # A restrained signature instrument suggests each myth; piano stays in front.
        if bar % 2 == 0 or voice in ('dual','sea'):
            for j in (0,2):
                if j < beats:
                    n = degree(phrase[(bar%4*4+j)%16])
                    pitch = lead_root+n+(12 if voice in ('ice','spark') else -12)
                    note(5,start+j+.03,1.65 if voice in ('wind','dual','horn') else .55,pitch,35*intensity)
        # Drum kit: eighth hats, firm rock backbeat, kicks shaped for each creature.
        if not (voice == 'ending' and bar < 4):
            for j in range(beats*2):
                off=j/2
                note(9,start+off,.12,51 if climax and not gentle else 42,(43 if j%2 else 57)*intensity)
            kick = [0,2] if beats==4 else [0,1.5]
            if voice in ('hunt','thunder','fire','serpent'):
                kick += [.5,2.5] if beats==4 else [.5]
            if voice=='thunder': kick += [1.75,3.5]
            if voice=='stone': kick=[0,.75,1.5,3]
            if voice=='trick': kick=[0,1.5,2.5]
            if gentle or bridge: kick=[0,2] if beats==4 else [0]
            for off in kick:
                if off < beats: note(9,start+off,.18,36,96*intensity)
            for off in ([1,3] if beats==4 else [2]):
                if t['id']=='fenrir' and bar%4==3 and off==3: continue
                note(9,start+off,.16,38,93*intensity)
                if not gentle: note(9,start+off+.008,.12,39,30*intensity)
            if bar%4==0 and (not gentle or bar>=4): note(9,start,.65,49,68*intensity)
            if bar%4==3 and not bridge and voice not in ('shop',):
                for j in range(4 if not gentle else 2):
                    off=beats-1+j/(4 if not gentle else 2)
                    note(9,start+off,.14,[38,48,47,45][j],(62+j*8)*intensity)
    programs = {0:0,1:0,2:27 if gentle else 30,3:27 if gentle else 29,4:33,5:{'ice':9,'spark':11,'horn':60,'wind':48,'dual':52,'sea':11,'raven':48,'echo':10,'thunder':60,'stone':60,'fire':48,'trick':10,'pluck':24}.get(voice,48),9:0}
    levels={0:110,1:77,2:66 if not gentle else 57,3:59 if not gentle else 50,4:96,5:42,9:100}
    pans={0:60,1:75,2:26,3:103,4:64,5:89,9:64}
    return bars, sorted(events), programs, levels, pans


def vlq(n):
    data=[n&127]; n>>=7
    while n: data.insert(0,(n&127)|128); n>>=7
    return bytes(data)


def midi(t,bars,events,programs,levels,pans,path):
    tempo=round(60_000_000/t['bpm'])
    data=b'\x00\xff\x51\x03'+tempo.to_bytes(3,'big')+b'\x00\xff\x58\x04'+bytes([t['beats'],2,24,8])
    for ch,p in programs.items():
        data+=b'\x00'+bytes([0xc0|ch,p])+b'\x00'+bytes([0xb0|ch,7,levels[ch]])+b'\x00'+bytes([0xb0|ch,10,pans[ch]])
    previous=0
    for pos,on,ch,p,v in events:
        tick=round(pos*480); data+=vlq(tick-previous)+bytes([(0x90 if on else 0x80)|ch,p,v]); previous=tick
    data+=vlq(round(bars*t['beats']*480)-previous)+b'\xff\x2f\x00'
    path.write_bytes(b'MThd'+struct.pack('>IHHH',6,0,1,480)+b'MTrk'+struct.pack('>I',len(data))+data)


class Synth:
    def __init__(self,library,soundfont):
        self.lib=C.CDLL(library)
        signatures={
          'new_fluid_settings':([],C.c_void_p),'fluid_settings_setnum':([C.c_void_p,C.c_char_p,C.c_double],C.c_int),
          'fluid_settings_setint':([C.c_void_p,C.c_char_p,C.c_int],C.c_int),'new_fluid_synth':([C.c_void_p],C.c_void_p),
          'fluid_synth_sfload':([C.c_void_p,C.c_char_p,C.c_int],C.c_int),'fluid_synth_program_change':([C.c_void_p,C.c_int,C.c_int],C.c_int),
          'fluid_synth_cc':([C.c_void_p,C.c_int,C.c_int,C.c_int],C.c_int),'fluid_synth_noteon':([C.c_void_p,C.c_int,C.c_int,C.c_int],C.c_int),
          'fluid_synth_noteoff':([C.c_void_p,C.c_int,C.c_int],C.c_int),
          'fluid_synth_write_float':([C.c_void_p,C.c_int,C.c_void_p,C.c_int,C.c_int,C.c_void_p,C.c_int,C.c_int],C.c_int),
          'delete_fluid_synth':([C.c_void_p],None),'delete_fluid_settings':([C.c_void_p],None)}
        for name,(args,result) in signatures.items():
            fn=getattr(self.lib,name); fn.argtypes=args; fn.restype=result
        self.settings=self.lib.new_fluid_settings()
        for key,value in [('synth.sample-rate',RATE),('synth.gain',.5),('synth.reverb.room-size',.46),('synth.reverb.damp',.45),('synth.reverb.level',.19),('synth.reverb.width',65)]:
            self.lib.fluid_settings_setnum(self.settings,key.encode(),value)
        self.lib.fluid_settings_setint(self.settings,b'synth.chorus.active',0)
        self.lib.fluid_settings_setint(self.settings,b'synth.cpu-cores',1)
        self.synth=self.lib.new_fluid_synth(self.settings)
        if self.lib.fluid_synth_sfload(self.synth,os.fsencode(soundfont),1)<0: raise RuntimeError('SoundFont load failed')
    def render(self,t,bars,events,programs,levels,pans):
        for ch,p in programs.items():
            self.lib.fluid_synth_program_change(self.synth,ch,p)
            for cc,value in ((7,levels[ch]),(10,pans[ch]),(91,24),(93,0)):
                self.lib.fluid_synth_cc(self.synth,ch,cc,value)
        frames=round(bars*t['beats']*60/t['bpm']*RATE)
        all_events=[]
        for cycle in range(3):
            all_events.extend((cycle*frames+round(pos*60/t['bpm']*RATE),on,ch,p,v) for pos,on,ch,p,v in events)
        output=np.empty((frames*3,2),np.float32)
        cursor=0
        for frame,on,ch,p,v in all_events+[(frames*3,0,0,0,0)]:
            count=frame-cursor
            if count:
                ptr=C.c_void_p(output[cursor:].ctypes.data)
                self.lib.fluid_synth_write_float(self.synth,count,ptr,0,2,ptr,1,2)
                cursor=frame
            if on: self.lib.fluid_synth_noteon(self.synth,ch,p,v)
            else: self.lib.fluid_synth_noteoff(self.synth,ch,p)
        return output[frames*2:].copy()
    def close(self):
        self.lib.delete_fluid_synth(self.synth); self.lib.delete_fluid_settings(self.settings)


def render(t,args):
    bars,events,programs,levels,pans=compose(t)
    dest=ROOT/'app/src/main/assets/music'; dest.mkdir(parents=True,exist_ok=True)
    work=Path(args.output); work.mkdir(parents=True,exist_ok=True)
    mid=ROOT/'tools/music/midi'/f"{t['id']}.mid"; mid.parent.mkdir(exist_ok=True)
    midi(t,bars,events,programs,levels,pans,mid)
    synth=Synth(args.library,args.soundfont)
    try: samples=synth.render(t,bars,events,programs,levels,pans)
    finally: synth.close()
    # DC removal + gentle saturation; preserve transients and leave room for game SE.
    samples-=np.mean(samples,axis=0)
    peak=float(np.max(np.abs(samples)))
    samples=np.tanh(samples*(1.4/max(peak,.01)))
    # Remove codec-edge discontinuity over 64 samples (1.45ms), with no beat-length gap.
    seam=(samples[0]+samples[-1])/2
    ramp=np.linspace(0,1,64,dtype=np.float32)[:,None]
    samples[:64]=seam*(1-ramp)+samples[:64]*ramp
    samples[-64:]=samples[-64:]*(1-ramp)+seam*ramp
    wav=work/f"{t['id']}.wav"
    with wave.open(str(wav),'wb') as w:
        w.setnchannels(2); w.setsampwidth(2); w.setframerate(RATE)
        w.writeframes((samples*30000).astype('<i2').tobytes())
    # Measure integrated loudness/true peak, then use ONE fixed gain over the entire loop.
    target=-17 if t['voice']=='ending' else -16 if t['voice']=='shop' else -15
    analysis=subprocess.run(['ffmpeg','-hide_banner','-i',str(wav),'-af',f'loudnorm=I={target}:TP=-2:LRA=9:print_format=json','-f','null','-'],capture_output=True,text=True,check=True)
    stats=json.JSONDecoder().raw_decode(analysis.stderr[analysis.stderr.rfind('{'):])[0]
    gain=min(target-float(stats['input_i']), -2.2-float(stats['input_tp']))
    filt=f"volume={gain:.6f}dB,atrim=end_sample={len(samples)}"
    ogg=dest/f"{t['id']}.ogg"
    subprocess.run(['ffmpeg','-v','error','-y','-i',str(wav),'-af',filt,'-ar',str(RATE),'-c:a','libvorbis','-q:a','5','-metadata',f"title={t['title']}",'-metadata','artist=BOSSRUSH','-metadata','comment=Original piano-rock arrangement; GeneralUser GS 2.0.3 instruments',str(ogg)],check=True)
    # WAV previews are decoded from the exact packaged file, not a separate mix.
    subprocess.run(['ffmpeg','-v','error','-y','-i',str(ogg),str(wav)],check=True)
    result={k:t[k] for k in ('id','name','title','bpm','beats','root','character')}
    result.update(file=f"{t['id']}.ogg",bars=bars,frames=len(samples),seconds=round(len(samples)/RATE,6),sample_rate=RATE,channels=2,
                  harmony=['bVI','bVII','i','i'],sha256=hashlib.sha256(ogg.read_bytes()).hexdigest(),midi_sha256=hashlib.sha256(mid.read_bytes()).hexdigest(),target_lufs=target,
                  note_count=sum(e[1] for e in events),instruments=['piano','electric guitar L/R','electric bass','rock drums','myth accent'])
    if t['id'] in SCORES:
        score=SCORES[t['id']]
        result.update(composition_version=2,form=score['form'],
                      score_sha256=hashlib.sha256(json.dumps(score,sort_keys=True,ensure_ascii=False).encode()).hexdigest())
    (work/f"{t['id']}.json").write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print(f"{t['id']}: {bars} bars, {len(samples)/RATE:.3f}s, {ogg.stat().st_size//1024} KiB",flush=True)
    return result


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--soundfont',required=True); parser.add_argument('--library',default='libfluidsynth.so.3')
    parser.add_argument('--output',default=str(ROOT/'artifacts/music-1.0.14'))
    selection=parser.add_mutually_exclusive_group()
    selection.add_argument('--only',nargs='+'); selection.add_argument('--battle',action='store_true')
    parser.add_argument('--jobs',type=int,default=1); args=parser.parse_args()
    assert hashlib.sha256(Path(args.soundfont).read_bytes()).hexdigest()==SF_SHA,'Unexpected instrument bank'
    scores=json.loads((ROOT/'tools/music/scores.json').read_text())
    selected=[t for t in scores if (not args.only or t['id'] in args.only) and (not args.battle or t['id'] in SCORES)]
    assert selected and (not args.only or set(args.only)<=set(t['id'] for t in scores)), 'Unknown track ID'
    manifest_path=ROOT/'app/src/main/assets/music/manifest.json'
    previous=json.loads(manifest_path.read_text())['tracks'] if manifest_path.exists() else []
    entries={t['id']:t for t in previous}
    if args.jobs > 1:
        with ProcessPoolExecutor(max_workers=args.jobs) as pool:
            rendered=list(pool.map(partial(render,args=args),selected))
    else:
        rendered=[render(t,args) for t in selected]
    entries.update({t['id']:t for t in rendered})
    if all(t['id'] in entries for t in scores):
        # Partial renders retain only manifest entries that still match shipped files.
        for t in entries.values():
            assert hashlib.sha256((manifest_path.parent/t['file']).read_bytes()).hexdigest()==t['sha256'],t['id']
            assert hashlib.sha256((ROOT/'tools/music/midi'/f"{t['id']}.mid").read_bytes()).hexdigest()==t['midi_sha256'],t['id']
        manifest={'version':2,'sample_rate':RATE,'channels':2,'silent_scenes':['title','jobs','codex','help','gameover'],
                  'soundfont':{'name':'GeneralUser GS 2.0.3','sha256':SF_SHA,'source_commit':'684543d5e5efaef08d02be50dcda8d552478fa60'},
                  'tracks':[entries[t['id']] for t in scores]}
        manifest_path.write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')

if __name__=='__main__': main()
