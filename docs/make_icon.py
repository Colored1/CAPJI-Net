from PIL import Image, ImageDraw, ImageFilter
import numpy as np, math
S=4
def lerp(a,b,t): return tuple(int(a[i]+(b[i]-a[i])*t) for i in range(3))
TEAL=(88,230,217); BLUE=(110,168,255); NAVY1=(9,14,30); NAVY2=(17,32,64)

def grad_v(w,h,c0,c1,y0=0.0,y1=1.0):
    t=np.clip((np.linspace(0,1,h)-y0)/(y1-y0),0,1)[:,None,None]
    a=np.array(c0)[None,None,:]; b=np.array(c1)[None,None,:]
    return np.broadcast_to(a+(b-a)*t,(h,w,3)).astype(np.uint8)

def bezier(p0,p1,p2,p3,n=60):
    return [((1-t)**3*p0[0]+3*(1-t)**2*t*p1[0]+3*(1-t)*t*t*p2[0]+t**3*p3[0],
             (1-t)**3*p0[1]+3*(1-t)**2*t*p1[1]+3*(1-t)*t*t*p2[1]+t**3*p3[1]) for t in [i/n for i in range(n+1)]]

def shield(cx,cy,u):
    """shield outline points, u = half height"""
    top=(cx,cy-u)
    pts=[]
    # right side: top -> shoulder -> down -> tip
    pts+=bezier((cx,cy-u),(cx+0.32*u,cy-u),(cx+0.62*u,cy-0.88*u),(cx+0.84*u,cy-0.74*u))
    pts+=bezier((cx+0.84*u,cy-0.74*u),(cx+0.86*u,cy-0.10*u),(cx+0.80*u,cy+0.30*u),(cx+0.52*u,cy+0.60*u))[1:]
    pts+=bezier((cx+0.52*u,cy+0.60*u),(cx+0.36*u,cy+0.78*u),(cx+0.16*u,cy+0.90*u),(cx,cy+u))[1:]
    left=[(2*cx-x,y) for x,y in reversed(pts)]
    return pts+left[1:]

def draw_art(N, with_bg):
    W=N*S; u=0.27*W; cx=cy=W/2
    out=Image.new('RGBA',(W,W),(0,0,0,0))
    if with_bg:
        bg=Image.fromarray(grad_v(W,W,NAVY2,NAVY1,0,1)).convert('RGBA')
        # radial teal glow
        yy,xx=np.mgrid[0:W,0:W]; d=np.hypot(xx-cx,yy-cy*1.05)/W
        glow=np.clip(1-d*1.9,0,1)**2.2
        g=np.zeros((W,W,4),np.uint8); g[...,0:3]=TEAL; g[...,3]=(glow*70).astype(np.uint8)
        bg.alpha_composite(Image.fromarray(g,'RGBA'))
        # radar rings
        ov=Image.new('RGBA',(W,W),(0,0,0,0)); r=ImageDraw.Draw(ov)
        for k,rad in enumerate((0.20,0.33,0.46)):
            R=rad*W; r.ellipse([cx-R,cy-R,cx+R,cy+R],outline=(88,230,217,60-k*14),width=max(2,int(W*0.004)))
        bg.alpha_composite(ov)
        out.alpha_composite(bg)
    # shield: soft shadow, body, rim
    pts=shield(cx,cy,u)
    sh=Image.new('RGBA',(W,W),(0,0,0,0)); ImageDraw.Draw(sh).polygon([(x,y+u*0.05) for x,y in pts],fill=(0,0,0,110))
    out.alpha_composite(sh.filter(ImageFilter.GaussianBlur(W*0.012)))
    mask=Image.new('L',(W,W),0); ImageDraw.Draw(mask).polygon(pts,fill=255)
    body=Image.fromarray(grad_v(W,W,(24,46,92),(10,18,40),(cy-u)/W,(cy+u)/W)).convert('RGBA'); body.putalpha(mask)
    out.alpha_composite(body)
    # rim gradient (teal bottom -> blue top)
    inner=Image.new('L',(W,W),0); ImageDraw.Draw(inner).polygon(shield(cx,cy+0.005*u,u*0.915),fill=255)
    from PIL import ImageChops
    rim_mask=ImageChops.subtract(mask,inner)
    rim=Image.fromarray(grad_v(W,W,BLUE,TEAL,(cy-u)/W,(cy+u)/W)).convert('RGBA'); rim.putalpha(rim_mask)
    out.alpha_composite(rim)
    # wifi
    wm=Image.new('L',(W,W),0); wd=ImageDraw.Draw(wm)
    dot=(cx,cy+0.26*u); sw=u*0.14
    for rad in (0.36,0.64,0.92):
        R=rad*u
        wd.arc([dot[0]-R,dot[1]-R,dot[0]+R,dot[1]+R],start=225,end=315,fill=255,width=int(sw))
        for ang in (225,315):
            a=math.radians(ang); mx=dot[0]+(R-sw/2)*math.cos(a); my=dot[1]+(R-sw/2)*math.sin(a)
            wd.ellipse([mx-sw/2,my-sw/2,mx+sw/2,my+sw/2],fill=255)
    rr=u*0.15; wd.ellipse([dot[0]-rr,dot[1]-rr,dot[0]+rr,dot[1]+rr],fill=255)
    # glow
    glow=Image.new('RGBA',(W,W),TEAL+(0,)); glow.putalpha(wm.filter(ImageFilter.GaussianBlur(W*0.016)).point(lambda v:int(v*0.55)))
    out.alpha_composite(glow)
    wf=Image.fromarray(grad_v(W,W,TEAL,(190,225,255),(dot[1]-u*0.95)/W,(dot[1]+rr)/W)[::-1] if False else grad_v(W,W,(210,240,255),TEAL,(dot[1]-u*0.95)/W,(dot[1]+0.1*u)/W)).convert('RGBA'); wf.putalpha(wm)
    out.alpha_composite(wf)
    # small "control" nodes on rim: 3 device dots
    nd=ImageDraw.Draw(out)
    return out.resize((N,N),Image.LANCZOS), (dot[0]/W,dot[1]/W)

res='/home/claude/capji-net/app/src/main/res/drawable-nodpi/'
fg,_=draw_art(432,False); fg.save(res+'ic_launcher_foreground.png')
bgl=Image.new('RGBA',(432,432)); 
full,dotpos=draw_art(432,True)
# background-only layer
W=432*S
bg=Image.fromarray(grad_v(432,432,NAVY2,NAVY1,0,1)).convert('RGBA')
yy,xx=np.mgrid[0:432,0:432]; d=np.hypot(xx-216,yy-227)/432
g=np.zeros((432,432,4),np.uint8); g[...,0:3]=TEAL; g[...,3]=(np.clip(1-d*1.9,0,1)**2.2*70).astype(np.uint8)
bg.alpha_composite(Image.fromarray(g,'RGBA'))
ov=Image.new('RGBA',(432*S,432*S),(0,0,0,0)); r=ImageDraw.Draw(ov)
for k,rad in enumerate((0.20,0.33,0.46)):
    R=rad*432*S; r.ellipse([216*S-R,216*S-R,216*S+R,216*S+R],outline=(88,230,217,60-k*14),width=2*S)
bg.alpha_composite(ov.resize((432,432),Image.LANCZOS))
bg.convert('RGB').save(res+'ic_launcher_background.png')
big,dp=draw_art(512,True); big.convert('RGB').save(res+'capji_mark.png')
print('dot frac',dp)
# preview: circular mask
prev=Image.alpha_composite(bg,fg)
m=Image.new('L',(432,432),0); ImageDraw.Draw(m).ellipse([0,0,431,431],fill=255)
c=Image.new('RGBA',(432,432),(30,30,30,255)); c.paste(prev,(0,0),m); c.save('/tmp/icon_preview2.png')
