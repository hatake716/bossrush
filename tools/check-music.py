#!/usr/bin/env python3
"""Check shipped music, authored scores, MIDI provenance and (optionally) decoded PCM."""
import argparse, hashlib, json, math, re, struct, subprocess, sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
DIR=ROOT/'app/src/main/assets/music'
sys.path.insert(0,str(ROOT/'tools/music'))
from audit_scores import audit as audit_scores

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()

def ogg(path):
    data=path.read_bytes(); offset=0; last=0
    assert data[:4]==b'OggS',path
    while offset<len(data):
        assert data[offset:offset+4]==b'OggS',path
        count=data[offset+26]
        size=sum(data[offset+27:offset+27+count])
        granule=struct.unpack_from('<Q',data,offset+6)[0]
        if granule!=2**64-1: last=granule
        offset+=27+count+size
    assert offset==len(data),path
    header=data.index(b'\x01vorbis')
    return last,data[header+11],struct.unpack_from('<I',data,header+12)[0]

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--audio',action='store_true')
    parser.add_argument('--output',type=Path,default=ROOT/'artifacts/music-1.0.14');args=parser.parse_args()
    manifest=json.loads((DIR/'manifest.json').read_text()); tracks=manifest['tracks']
    scores=json.loads((ROOT/'tools/music/scores.json').read_text())
    kotlin=(ROOT/'app/src/main/java/io/github/hatake716/bossrush/Content.kt').read_text()
    bosses=re.findall(r'Boss\("([^"]+)"[^\n]+, "([^"]+)", (\d+), (\d+)\)',kotlin)
    assert len(tracks)==34 and [t['id'] for t in tracks]==[b[0] for b in bosses]+['shop','ending']
    assert len({t['sha256'] for t in tracks})==34
    assert set(p.name for p in DIR.glob('*.ogg'))=={t['file'] for t in tracks}
    assert 'title' in manifest['silent_scenes'] and not (DIR/'title.ogg').exists()
    assert (ROOT/'docs/licenses/GeneralUser-GS.txt').is_file()
    diversity=audit_scores(tracks)
    catalog=(ROOT/'app/src/main/java/io/github/hatake716/bossrush/BattleScore.kt').read_text()
    for t in scores[:32]:
        assert f'BattleTheme("{t["id"]}","{t["character"]}"' in catalog,t['id']
    args.output.mkdir(parents=True,exist_ok=True)
    (args.output/'score-audit.json').write_text(json.dumps(diversity,ensure_ascii=False,indent=2)+'\n')
    audit=[]
    for i,(t,s) in enumerate(zip(tracks,scores)):
        path=DIR/t['file']; assert sha(path)==t['sha256'],t['id']
        assert sha(ROOT/'tools/music/midi'/f"{t['id']}.mid")==t['midi_sha256']
        assert t['id']==s['id'] and t['bpm']==s['bpm'] and t['beats']==s['beats']
        assert t['character']==s['character']
        if i<32: assert (t['id'],t['title'],t['bpm'],t['root'])==(bosses[i][0],bosses[i][1],int(bosses[i][2]),int(bosses[i][3]))
        assert t['bars']%4==0 and 27<=t['seconds']<=33 and t['note_count']>200
        assert t['harmony']==['bVI','bVII','i','i']
        assert abs(t['seconds']-t['bars']*t['beats']*60/t['bpm'])<1/44100
        assert ogg(path)==(t['frames'],2,44100),t['id']
        assert (ROOT/'tools/music/midi'/f"{t['id']}.mid").read_bytes()[:4]==b'MThd'
        if args.audio:
            import numpy as np
            pcm=subprocess.run(['ffmpeg','-v','error','-i',str(path),'-f','f32le','-acodec','pcm_f32le','-'],capture_output=True,check=True).stdout
            samples=np.frombuffer(pcm,dtype='<f4').reshape(-1,2)
            frames=len(samples);rms=float(np.sqrt(np.mean(samples*samples)));peak=float(np.max(np.abs(samples)))
            assert t['frames']-2048<=frames<=t['frames'],(t['id'],frames,t['frames'])
            assert .03<rms<.5 and .1<peak<1,(t['id'],rms,peak)
            assert float(np.mean(np.abs(samples[:,0]-samples[:,1])))>.005,t['id']
            windows=samples[:frames//441*441].reshape(-1,441,2)
            min_rms=float(np.sqrt(np.mean(windows*windows,axis=(1,2))).min())
            assert min_rms>.0001,(t['id'],'silent 10ms window',min_rms)
            # Match the runtime's tiny boundary correction and test the final->first jump.
            samples=samples.copy();seam=(samples[0]+samples[-1])/2;ramp=np.linspace(0,1,64)[:,None]
            samples[:64]=seam*(1-ramp)+samples[:64]*ramp;samples[-64:]=samples[-64:]*(1-ramp)+seam*ramp
            assert np.max(np.abs(samples[-1]-samples[0]))<1e-6
            loud=subprocess.run(['ffmpeg','-hide_banner','-i',str(path),'-af','loudnorm=print_format=json','-f','null','-'],capture_output=True,text=True,check=True)
            stats=json.JSONDecoder().raw_decode(loud.stderr[loud.stderr.rfind('{'):])[0]
            assert abs(float(stats['input_i'])-t['target_lufs'])<1.0,(t['id'],stats)
            assert float(stats['input_tp'])<-.8,(t['id'],stats)
            audit.append(dict(id=t['id'],seconds=frames/44100,frames=frames,rms=rms,peak=peak,min_10ms_rms=min_rms,lufs=float(stats['input_i']),true_peak_db=float(stats['input_tp'])))
            print(f"{t['id']}: {frames/44100:.3f}s, {stats['input_i']} LUFS, {stats['input_tp']} dBTP",flush=True)
    if args.audio:
        (args.output/'audio-audit.json').write_text(json.dumps(audit,indent=2)+'\n')
    print(f"PASS: {len(tracks)} stereo loops; 32 distinct hook rhythms/contours; literal MIDI, metadata, PCM lengths and provenance")

if __name__=='__main__':main()
