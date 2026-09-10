"""Regression checks for actual scored notes; uniqueness is not a listening test."""
import difflib
import hashlib
import itertools
import json
from pathlib import Path
from battle_scores import SCORES, phrase, validate
from battle_arranger import compose_battle, degree

ROOT = Path(__file__).resolve().parents[2]


def midi_notes(path):
    data=path.read_bytes()
    assert data[:14] == b'MThd\x00\x00\x00\x06\x00\x00\x00\x01\x01\xe0'
    assert data[14:18] == b'MTrk'
    cursor, tick, notes = 22, 0, []
    def variable():
        nonlocal cursor
        value=0
        while True:
            b=data[cursor]; cursor+=1; value=(value<<7)|(b&127)
            if b<128: return value
    while cursor<len(data):
        tick+=variable(); status=data[cursor]; cursor+=1
        if status==255:
            cursor+=1; size=variable(); cursor+=size
        elif status>>4 in (8,9,11):
            a,b=data[cursor:cursor+2]; cursor+=2
            if status>>4 in (8,9): notes.append((tick,1 if status>>4==9 else 0,status&15,a,b))
        elif status>>4==12:
            cursor+=1
        else: raise AssertionError(('Unexpected authored MIDI event',status))
    return notes


def audit(tracks):
    validate()
    assert [t['id'] for t in tracks[:32]] == list(SCORES)
    rows=[]
    for t in tracks[:32]:
        id=t['id']; s=SCORES[id]; beats=t['beats']
        bars,events,*_=compose_battle(t)
        # Verify the delivered MIDI, not just hashes or the input note arrays.
        expected=[(round(p*480),on,ch,n,v) for p,on,ch,n,v in events]
        assert midi_notes(ROOT/'tools/music/midi'/f'{id}.mid')==expected, (id,'score/MIDI mismatch')
        assert t['composition_version']==2 and t['form']==s['form']
        assert t['score_sha256']==hashlib.sha256(json.dumps(s,sort_keys=True,ensure_ascii=False).encode()).hexdigest()
        assert t['character']==s['character']
        assert s['a']!=s['b']
        notes=[(float(p),float(d),degree(n)) for p,d,n in phrase(s['a'],beats) if n is not None]
        intervals=[b[2]-a[2] for a,b in zip(notes,notes[1:])]
        onsets=[round(p/beats,6) for p,d,n in notes]
        durations=[round(d/beats,6) for p,d,n in notes]
        rows.append(dict(id=id,hook_notes=len(notes),pitch_range=max(n[2] for n in notes)-min(n[2] for n in notes),
                         rest_beats=float(sum(d for p,d,n in phrase(s['a'],beats) if n is None)),
                         intervals=intervals,onsets=onsets,durations=durations,form=s['form']))
        assert bars*beats == len(s['form'])*4*beats
    assert len({tuple(r['intervals']) for r in rows})==32, 'A hook is a transposition of another'
    assert len({tuple(r['onsets']) for r in rows})==32, 'Shared melody-onset template returned'
    assert len({SCORES[r['id']]['riff'] for r in rows})>=28, 'Shared guitar pattern returned'
    pairs=[]
    for a,b in itertools.combinations(rows,2):
        ratio=difflib.SequenceMatcher(None,a['intervals'],b['intervals'],autojunk=False).ratio()
        pairs.append(dict(a=a['id'],b=b['id'],interval_sequence_similarity=round(ratio,4)))
    pairs.sort(key=lambda p:p['interval_sequence_similarity'],reverse=True)
    # Catches long reused pitch contours even when tempo, key and sound change.
    assert pairs[0]['interval_sequence_similarity']<.8, pairs[0]
    return dict(tracks=rows,closest_contours=pairs[:10],unique_onset_patterns=32,
                unique_transposition_independent_contours=32,
                note='Score comparison is a regression guard, not proof of perceived musical quality.')


if __name__=='__main__':
    tracks=json.loads((ROOT/'app/src/main/assets/music/manifest.json').read_text())['tracks']
    print(json.dumps(audit(tracks),ensure_ascii=False,indent=2))
