import math,sys,os
out=sys.argv[1]
BG="#16A34A"; FG="#FFFFFF"; RED="#FF4D4F"
cx,cy,R=54,54,24; s=R/22
def pt(r,a,ox=cx,oy=cy): return (ox+r*math.cos(math.radians(a)), oy+r*math.sin(math.radians(a)))
def poly(ps): return "M"+" L".join(f"{x:.2f},{y:.2f}" for x,y in ps)+" Z"
angs=[-90+72*k for k in range(5)]
center=poly([pt(7.5*s,a) for a in angs])
patches=[]; seams=[]
for a in angs:
    pc=pt(20.5*s,a)
    patches.append(poly([pt(7.5*s,a+180+72*k,*pc) for k in range(5)]))
    p1=pt(7.5*s,a); p2=pt((20.5-7.5)*s,a)
    seams.append(f"M{p1[0]:.2f},{p1[1]:.2f} L{p2[0]:.2f},{p2[1]:.2f}")
rim=f"M{cx-R+1.5},{cy} a{R-1.5},{R-1.5} 0 1,0 {2*(R-1.5)},0 a{R-1.5},{R-1.5} 0 1,0 {-2*(R-1.5)},0 Z"
bx,by,br=70,70,10.5
ball=f"M{cx-R},{cy} a{R},{R} 0 1,0 {2*R},0 a{R},{R} 0 1,0 {-2*R},0 Z"
gap=f"M{bx-br-2.5},{by} a{br+2.5},{br+2.5} 0 1,0 {2*(br+2.5)},0 a{br+2.5},{br+2.5} 0 1,0 {-2*(br+2.5)},0 Z"
badge=f"M{bx-br},{by} a{br},{br} 0 1,0 {2*br},0 a{br},{br} 0 1,0 {-2*br},0 Z"
ri=6.2; d=ri/math.sqrt(2)
ring=f"M{bx-ri},{by} a{ri},{ri} 0 1,0 {2*ri},0 a{ri},{ri} 0 1,0 {-2*ri},0 Z"
slash=f"M{bx-d:.2f},{by-d:.2f} L{bx+d:.2f},{by+d:.2f}"
# ---- SVG preview
svg=f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="432" height="432">
<defs><clipPath id="c"><path d="{ball}"/></clipPath><mask id="m"><rect width="108" height="108" fill="#fff"/><circle cx="54" cy="54" r="33" fill="none"/></mask></defs>
<rect width="108" height="108" fill="{BG}"/>
<path d="{ball}" fill="{FG}"/>
<g fill="{BG}">{''.join(f'<path d="{p}"/>' for p in patches)}</g>
<path d="{rim}" fill="none" stroke="{FG}" stroke-width="3"/>
<path d="{center}" fill="{BG}"/>
<path d="{' '.join(seams)}" stroke="{BG}" stroke-width="2.6" stroke-linecap="round"/>
<path d="{gap}" fill="{BG}"/>
<path d="{badge}" fill="{RED}"/>
<path d="{ring}" fill="none" stroke="{FG}" stroke-width="2.8"/>
<path d="{slash}" stroke="{FG}" stroke-width="2.8" stroke-linecap="round"/>
</svg>'''
open(os.path.join(out,"assets/icon/icon.svg"),"w").write(svg)
# ---- Android adaptive icon
def vec(body):
    return f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
{body}</vector>
'''
def fill(d,c): return f'    <path android:fillColor="{c}" android:pathData="{d}"/>\n'
def stroke(d,c,w): return f'    <path android:strokeColor="{c}" android:strokeWidth="{w}" android:strokeLineCap="round" android:pathData="{d}"/>\n'
clipgroup="".join(fill(p,BG) for p in patches)
fg=fill(ball,FG)+clipgroup+stroke(rim,FG,3)+fill(center,BG)+stroke(" ".join(seams),BG,2.6)+fill(gap,BG)+fill(badge,RED)+stroke(ring,FG,2.8)+stroke(slash,FG,2.8)
# 单色（主题图标）：只保留形状，用描边/挖空不可行处只画轮廓
mono=fill(ball,"#FF000000")+clipgroup.replace(BG,"#00000000")
res=os.path.join(out,"assets/icon/android/res")
for d in ["drawable","mipmap-anydpi-v26","values"]: os.makedirs(os.path.join(res,d),exist_ok=True)
open(os.path.join(res,"drawable/ic_launcher_foreground.xml"),"w").write(vec(fg))
mono=fill(ball,"#FF000000")+fill(badge,"#FF000000")
open(os.path.join(res,"drawable/ic_launcher_monochrome.xml"),"w").write(vec(mono))
open(os.path.join(res,"values/ic_launcher_colors.xml"),"w").write(f'<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_background">{BG}</color>\n</resources>\n')
ai='''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
    <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>
</adaptive-icon>
'''
for n in ["ic_launcher.xml","ic_launcher_round.xml"]: open(os.path.join(res,"mipmap-anydpi-v26",n),"w").write(ai)
