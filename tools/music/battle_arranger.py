"""Turn the individually authored scores into MIDI events without rewriting hooks."""
import math
from collections import defaultdict
from battle_scores import SCORES, phrase

SCALE = [0, 2, 3, 5, 7, 8, 10]
CHORDS = [[8, 12, 15], [10, 14, 17], [0, 3, 7], [0, 3, 7]]


def degree(n):
    return n // 7 * 12 + SCALE[n % 7]


def compose_battle(t):
    s = SCORES[t['id']]
    beats, pc = t['beats'], t['root'] % 12
    bars = int(math.floor(30 * t['bpm'] / (60 * beats * 4) + .5)) * 4
    assert len(s['form']) == bars // 4, (t['id'], bars, s['form'])
    lead, low, bass = 60 + pc + s['register'], 36 + pc, 24 + pc
    held = defaultdict(list)

    def note(ch, pos, dur, pitch, vel):
        pos, dur = float(pos), float(dur)
        if not 0 <= pos < bars * beats or dur <= 0:
            return
        assert 21 <= pitch <= 108, (t['id'], pitch, ch)
        held[ch, pitch].append([pos, min(pos + dur, bars * beats - .005), round(max(1, min(127, vel)))])

    for block, section in enumerate(s['form']):
        kind = 'b' if section in 'BbY' else 'a'
        motif = phrase(s[kind], beats)
        light, peak = section.islower(), section in 'XY'
        strength = .86 if light else 1.04 if peak else 1
        start = block * 4 * beats

        # Play each score literally: the hook retains its rests, ties and intervals.
        for pos, dur, n in motif:
            if n is None:
                continue
            vel = (96 if pos.denominator == 1 else 81) * strength
            note(0, start+pos, dur*s['gate'], lead+degree(n), vel)
            if peak and (dur >= 1 or s['left'] == 'hammer'):
                note(0, start+pos, dur*s['gate']*.93, lead+degree(n)-12, vel*.66)
            if s['left'] in ('canon','web','answer','ravens'):
                # Distinctly timed answers occupy the left-hand channel in the bridge.
                delay = {'canon':2, 'web':.5, 'answer':1, 'ravens':1.5}[s['left']]
                if light or (s['left'] in ('canon','web') and block % 2 == 1):
                    note(1, start+pos+delay, dur*.72, lead+degree(n)-12, 49)
            if dur >= 1.5 and (int(pos)//beats) % 2 == 0:
                note(5, start+pos+.02, min(float(dur)*.85,3), lead+degree(n)-12, 39*strength)

        for b in range(4):
            bar = block*4+b
            at, chord = bar*beats, CHORDS[b]
            style = s['left']
            # Whole-band breaks belong to the composed rhetoric, never to loop padding.
            def space(pos):
                if t['id'] == 'fenrir':
                    return (b==3 and pos>=2) or (b==0 and pos<1) or (b==2 and pos<1.5)
                return t['id']=='tyr' and b==3 and 2<=pos<3

            # The left hand spells every complete major/minor chord, with several
            # rhythmic idioms instead of a universal eighth-note Alberti template.
            if style in ('block','funeral','oath','march','herald','triangle','breaks','stabs','octaves','hammer'):
                attacks = ([0] if style=='funeral' else [0,1.5,3] if style=='triangle'
                           else [0,1,2,3] if style in ('oath','hammer') else [0,2])
                for off in attacks:
                    if off>=beats or space(off): continue
                    for n in chord:
                        note(1,at+off,1.8 if style in ('block','funeral') else .55,low+n,48*strength)
            else:
                plans = {
                    'skip':([0,1.5,2,3.5],[0,2,1,2]),
                    'stride':([0,1,2,3],[0,2,0,1]),
                    'answer':([0,2.5],[0,2]),
                    'canon':([0,1,2,3],[0,1,2,1]),
                    'drive':([i*.5 for i in range(8)],[0,1,2,1]),
                    'lowarp':([0,.75,1.5,2.5,3],[2,1,0,1,2]),
                    'wide':([0,1.5,3],[0,1,2]),
                    'fall':([0,.5,1,2,3],[2,1,0,2,1]),
                    'crystal':([0,1.5,3],[0,2,1]),
                    'pluck':([0,1,2,8/3,10/3],[0,2,1,2,0]),
                    'waves':([i/3 for i in range(12)],[0,1,2,2,1,0]),
                    'web':([0,.75,1.75,2.5,3.5],[0,2,1,0,2]),
                    'sail':([0,.5,1.5,2.5,3.5],[0,1,2,1,2]),
                    'coil':([0,.75,1.5,2,2.75,3.5],[0,1,2,2,1,0]),
                    'roots':([0,.25,1,2.25,3],[2,2,1,0,1]),
                    'mirror':([0,.75,1.75,2.5,3.25],[0,2,1,2,0]),
                    'flames':([i*.5 for i in range(8)],[0,1,2,0,1,2,1,2]),
                    'buds':([0,1,1.75,2.5,3.5],[0,1,2,1,2]),
                    'dance':([0,2/3,4/3,2,8/3,10/3],[0,2,1,2,0,1]),
                    'jewels':([0,.5,1.5,2,3.5],[0,2,1,0,2]),
                    'waltz':([0,1,2],[0,1,2]),
                    'ravens':([0,.5,1,2,3],[0,2,1,2,0]),
                }
                positions, order = plans[style]
                for j,off in enumerate(positions):
                    note(1,at+off,.85 if style in ('wide','sail') else .38,
                         low+chord[order[j%len(order)]]+(12 if style in ('crystal','buds') else 0),
                         (44 if j%2==0 else 36)*strength)
                # Ensure the complete harmony remains audible even with sparse arps.
                for n in chord: note(1,at,.4,low+n,28)

            for off,dur,n in phrase(s['riff'],beats):
                if n is None or space(off): continue
                if light and off >= beats/2: continue
                for interval in (0,7,12):
                    note(2,at+off,min(float(dur)*.8,2.8),low+chord[0]+n+interval,72*strength)
                # A sustained right guitar in lyrical sections; short unisons in riffs.
                if s['left'] in ('wide','funeral','canon','sail','ravens'):
                    if off==0:
                        for interval in (0,7,12): note(3,at+.015,beats-.08,low+chord[0]+interval,49)
                else:
                    for interval in (0,7): note(3,at+off+.015,float(dur)*.73,low+chord[0]+n+interval,58*strength)
            for off,dur,n in phrase(s['bass'],beats):
                if n is None or space(off): continue
                if n in (3,4): n=chord[1]-chord[0]
                note(4,at+off,dur*.9,bass+chord[0]+n,82*strength)

            # Rock beats use each score's kick/snare pattern. Shuffle hats actually
            # swing; triplet rides, straight sixteenths and half-time are distinct.
            hat_positions = ([i+v for i in range(beats) for v in (0,2/3)]
                             if style=='waves' else [i/s['hats'] for i in range(beats*s['hats'])])
            for j,off in enumerate(hat_positions):
                if space(off) or (light and j%2): continue
                cymbal = 51 if (peak and style not in ('crystal','skip','mirror')) else 42
                note(9,at+off,.1,cymbal,(51 if off%1==0 else 33)*strength)
            for off in s['kick']:
                if not space(off): note(9,at+off,.14,36,100*strength)
            for off in s['snare']:
                if not space(off): note(9,at+off,.16,38,95*strength)
            if style in ('octaves','triangle','hammer'):
                for j,off in enumerate(s['kick']):
                    if j%2==0 and not space(off): note(9,at+off,.2,45 if j%3 else 41,64)
            if b==0 and not space(0): note(9,at,.65,49,66 if not light else 40)
            # Fills occur at section boundaries, respecting each authored breath.
            if b==3 and not light and not space(beats-1):
                fill = ([0,1/3,2/3] if s['hats']==3 else [0,.5] if s['hats']==1 else [0,.25,.5,.75])
                for j,off in enumerate(fill):
                    if not space(beats-1+off): note(9,at+beats-1+off,.14,[48,47,45,43][j],66+j*6)

    # Retriggering one MIDI key must not leave an old note-off that cuts the new note.
    events=[]
    for (ch,pitch), spans in held.items():
        merged={}
        for on,off,v in sorted(spans):
            if on in merged: merged[on]=[max(off,merged[on][0]),max(v,merged[on][1])]
            else: merged[on]=[off,v]
        starts=sorted(merged)
        for j,on in enumerate(starts):
            off,v=merged[on]
            if j+1<len(starts): off=min(off,starts[j+1])
            events += [(on,1,ch,pitch,v),(off,0,ch,pitch,0)]
    programs={0:0,1:0,2:30,3:29,4:33,5:s['accent'],9:0}
    levels={0:116,1:68,2:64,3:51,4:93,5:42,9:100}
    pans={0:62,1:78,2:23,3:105,4:64,5:40,9:64}
    return bars,sorted(events),programs,levels,pans
