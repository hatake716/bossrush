#!/usr/bin/env python3
"""Check all 32 chapter identities and the actual bundled Japanese pixel font."""
import hashlib, json, re, struct
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'app/src/main/java/io/github/hatake716/bossrush'


def font_has(data,cp):
    tables={}
    for i in range(struct.unpack_from('>H',data,4)[0]):
        tag,_,offset,size=struct.unpack_from('>4sIII',data,12+16*i);tables[tag]=(offset,size)
    off=tables[b'cmap'][0]
    for i in range(struct.unpack_from('>H',data,off+2)[0]):
        platform,encoding,sub=struct.unpack_from('>HHI',data,off+4+8*i)
        at=off+sub
        if platform!=3 or encoding!=1 or struct.unpack_from('>H',data,at)[0]!=4: continue
        n=struct.unpack_from('>H',data,at+6)[0]//2
        end_at=at+14; start_at=end_at+2*n+2; delta_at=start_at+2*n; range_at=delta_at+2*n
        for j in range(n):
            first=struct.unpack_from('>H',data,start_at+2*j)[0]; last=struct.unpack_from('>H',data,end_at+2*j)[0]
            if not first<=cp<=last: continue
            delta=struct.unpack_from('>h',data,delta_at+2*j)[0]; rel=struct.unpack_from('>H',data,range_at+2*j)[0]
            if rel==0:return ((cp+delta)&65535)!=0
            glyph=struct.unpack_from('>H',data,range_at+2*j+rel+2*(cp-first))[0]
            return glyph!=0 and ((glyph+delta)&65535)!=0
    return False


def main():
    source=(SRC/'MainStory.kt').read_text()
    chapters=re.findall(r'chapter\("([^"]+)","([^"]+)","([^"]+)",0x([0-9a-f]+),"([^"]+)"',source)
    bosses=re.findall(r'Boss\("([^"]+)"',(SRC/'Content.kt').read_text())
    assert [r[0] for r in chapters]==bosses and len(chapters)==32
    assert len({r[1] for r in chapters})==32 and len({r[2] for r in chapters})==32
    fonts=ROOT/'app/src/main/assets/fonts';manifest=json.loads((fonts/'manifest.json').read_text())
    data=(fonts/manifest['file']).read_bytes()
    assert hashlib.sha256(data).hexdigest()==manifest['sha256']
    assert (ROOT/manifest['license']).is_file()
    strings=re.findall(r'"([^"\n]+)"',source)
    glyphs={ord(c) for text in strings for c in text if ord(c)>=128}
    assert all(font_has(data,c) for c in glyphs),'Missing glyphs: '+''.join(chr(c) for c in glyphs if not font_has(data,c))
    print(f'PASS: 32 story chapters, 32 recovered colors, source references and {len(glyphs)} Japanese pixel-font glyphs')


if __name__=='__main__': main()
