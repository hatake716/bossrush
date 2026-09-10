#!/usr/bin/env python3
"""Make a local 32-track audition page and an 8-track excerpt from the shipped Oggs."""
import argparse, html, json, subprocess
from pathlib import Path
import numpy as np
from render import Synth, compose, RATE, ROOT


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--output',type=Path,default=ROOT/'artifacts/music-1.0.14')
    p.add_argument('--soundfont',required=True);p.add_argument('--library',required=True)
    args=p.parse_args();args.output.mkdir(parents=True,exist_ok=True)
    tracks=json.loads((ROOT/'tools/music/scores.json').read_text())[:32]
    cards=[]
    for i,t in enumerate(tracks):
        id=t['id']; source=ROOT/'app/src/main/assets/music'/f'{id}.ogg'
        # Full preview is byte-for-byte the game recording (decoded, no remix).
        subprocess.run(['ffmpeg','-v','error','-y','-i',str(source),str(args.output/f'{id}.wav')],check=True)
        bars,events,programs,levels,pans=compose(t)
        events=[e for e in events if e[2]==0 and e[0]<4*t['beats']]
        synth=Synth(args.library,args.soundfont)
        try: samples=synth.render(t,4,events,{0:0},{0:116},{0:64})
        finally: synth.close()
        # This labelled solo excerpt is for inspecting the hook, not the in-game mix.
        samples*=.65/max(.01,float(np.max(np.abs(samples))))
        subprocess.run(['ffmpeg','-v','error','-y','-f','f32le','-ar',str(RATE),'-ac','2','-i','-',str(args.output/f'{id}-piano.wav')],input=samples.astype('<f4').tobytes(),check=True)
        old=args.output.parent/'music-1.0.12'/f'{id}.wav'
        previous=f'<details><summary>変更前を聴く</summary><audio controls preload="none" src="../music-1.0.12/{id}.wav"></audio></details>' if old.exists() else ''
        cards.append(f'<article><small>{i+1:02d} · {t["bpm"]} BPM · {t["beats"]}/4</small><h2>{html.escape(t["name"])}</h2><h3>{html.escape(t["title"])}</h3><p>{html.escape(t["character"])}</p><label>新曲 · ゲーム収録版<audio controls loop preload="none" src="{id}.wav"></audio></label><details><summary>主題Aをピアノだけで聴く</summary><audio controls preload="none" src="{id}-piano.wav"></audio></details>{previous}</article>')
    page='''<!doctype html><html lang="ja"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>BOSSRUSH / 32 melodies</title><style>
    *{box-sizing:border-box}body{margin:0;background:#101817;color:#e4edcf;font:16px/1.7 system-ui,sans-serif}header,main{max-width:1200px;margin:auto;padding:32px}header{border-bottom:1px solid #587068}h1{font-size:clamp(28px,5vw,52px);letter-spacing:.06em;margin:0}header p{max-width:850px;color:#b5c4b7}small{color:#afbd72;letter-spacing:.1em}main{display:grid;grid-template-columns:repeat(auto-fit,minmax(290px,1fr));gap:20px}article{background:#1a2825;border:1px solid #3d5349;padding:22px;border-radius:12px}h2{margin:4px 0;font-size:23px}h3{margin:0;font-size:15px;color:#b3c799}article p{min-height:80px}audio{width:100%;display:block;margin:10px 0 16px}details{margin:10px 0;font-size:14px}summary{cursor:pointer}label{font-size:14px}button{background:#b9cd83;border:0;border-radius:6px;padding:10px 20px;font:inherit;color:#10231b;cursor:pointer}
    </style><header><small>BOSSRUSH · DEVELOPMENT 1.0.14</small><h1>32の神々、32の主旋律。</h1><p>ZUN進行・ピアノ・ロックを軸に、音の長さ、休符、跳躍、バンドの刻みまで書き分けました。「主題A」では旋律だけを、変更前との比較では編曲の変化を確認できます。新曲の通常プレーヤーはゲームに収録した音声です。新曲は1周約30秒でループします。</p><button id="stop">すべて停止</button></header><main>'''+''.join(cards)+'''</main><script>
    const players=[...document.querySelectorAll('audio')];players.forEach(p=>p.addEventListener('play',()=>players.forEach(q=>{if(q!==p)q.pause()})));document.querySelector('#stop').onclick=()=>players.forEach(p=>p.pause());
    </script></html>'''
    (args.output/'index.html').write_text(page)
    ids=['ratatoskr','hel','aegir','fenrir','skadi','loki','thor','odin']
    chunks=[]
    for id in ids:
        data=subprocess.run(['ffmpeg','-v','error','-i',str(args.output/f'{id}.wav'),'-t','7','-af','afade=t=out:st=6.85:d=0.15','-f','f32le','-ar',str(RATE),'-ac','2','-'],capture_output=True,check=True).stdout
        chunks.append(data)
    subprocess.run(['ffmpeg','-v','error','-y','-f','f32le','-ar',str(RATE),'-ac','2','-i','-','-c:a','libmp3lame','-b:a','192k',str(args.output/'boss-melodies-8tracks.mp3')],input=b''.join(chunks),check=True)
    (args.output/'preview-order.json').write_text(json.dumps([dict(id=id,from_seconds=i*7,to_seconds=(i+1)*7) for i,id in enumerate(ids)],indent=2)+'\n')
    print(args.output/'index.html')


if __name__=='__main__':main()
