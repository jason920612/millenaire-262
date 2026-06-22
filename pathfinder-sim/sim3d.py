#!/usr/bin/env python3
"""
Python mirror of org.millenaire.common.ai.nav.Mill3DPathfinder — same move-action graph (walk 8-way w/ no
corner-cutting, step/jump UP one, DROP up to 5, JUMP a 1-block gap), same lexicographic-ish scalar cost
(climbing free, drops/jumps penalised), same reach-adjacent-for-non-standable-goal and best-effort partial
path. Lets us SIMULATE + VISUALISE 3D routes over nasty terrain fast, then trust the Java port.

A Voxel is just a predicate solid(x,y,z). standable/clearBody match the Java exactly.
"""
import heapq, math

DIAG, JUMP_GAP, DROP = 1.4142, 2.5, 0.5
H8 = [(1,0),(-1,0),(0,1),(0,-1),(1,1),(1,-1),(-1,1),(-1,-1)]
H4 = [(1,0),(-1,0),(0,1),(0,-1)]


class Voxel:
    def __init__(self, solid, tall=None):   # solid: set of (x,y,z); tall: fence/wall cells (1.5 high)
        self.s = solid
        self.t = tall or set()
    def solid(self, x,y,z): return (x,y,z) in self.s
    def tall(self, x,y,z):  return (x,y,z) in self.t   # >1-block collision: can't be jumped onto
    def standable(self, x,y,z):
        return self.solid(x,y-1,z) and not self.solid(x,y,z) and not self.solid(x,y+1,z)
    def clearBody(self, x,y,z):
        return not self.solid(x,y,z) and not self.solid(x,y+1,z)


def neighbors(v, p):
    x,y,z = p
    out = []
    for dx,dz in H8:
        nx,nz = x+dx, z+dz
        diag = dx != 0 and dz != 0
        base = DIAG if diag else 1.0
        if diag and (v.solid(nx,y,z) or v.solid(x,y,nz) or v.solid(nx,y+1,z) or v.solid(x,y+1,nz)):
            continue
        if v.standable(nx,y,nz):
            out.append(((nx,y,nz), base))
        elif v.standable(nx,y+1,nz) and not v.tall(nx,y,nz) and v.clearBody(x,y+2,z) and not v.solid(nx,y+2,nz):
            out.append(((nx,y+1,nz), base))   # NOT onto a fence/wall (1.5 tall — un-jumpable)
        elif v.clearBody(nx,y,nz):
            dy = -1
            while dy >= -5:
                if v.standable(nx,y+dy,nz):
                    out.append(((nx,y+dy,nz), base + DROP*(-dy))); break
                if v.solid(nx,y+dy-1,nz): break
                dy -= 1
    for dx,dz in H4:
        gx,gz = x+dx, z+dz
        lx,lz = x+2*dx, z+2*dz
        if (not v.standable(gx,y,gz) and v.clearBody(gx,y,gz) and v.standable(lx,y,lz)
                and v.clearBody(lx,y,lz) and not v.solid(gx,y+1,gz)):
            out.append(((lx,y,lz), JUMP_GAP))
    for dx,dz in H4:                                       # running jump over a 2-block gap (land 3 out)
        g1x,g1z = x+dx, z+dz; g2x,g2z = x+2*dx, z+2*dz; lx,lz = x+3*dx, z+3*dz
        if (not v.standable(g1x,y,g1z) and v.clearBody(g1x,y,g1z)
                and not v.standable(g2x,y,g2z) and v.clearBody(g2x,y,g2z)
                and v.standable(lx,y,lz) and v.clearBody(lx,y,lz)
                and not v.solid(g1x,y+1,g1z) and not v.solid(g2x,y+1,g2z)):
            out.append(((lx,y,lz), JUMP_GAP+1.5))
    return out


def manhattan(a,b): return abs(a[0]-b[0])+abs(a[1]-b[1])+abs(a[2]-b[2])
def closer(a,b,d):  return (a[0]-b[0])**2+(a[1]-b[1])**2+(a[2]-b[2])**2 < d*d


def find_path(v, start, goal, max_nodes=60000, danger=lambda x,y,z:0.0):
    g = {start: 0.0}
    came = {}
    open_h = [(manhattan(start,goal), 0.0, start)]
    best, bestd = start, float(manhattan(start,goal))
    expanded = 0
    while open_h and expanded < max_nodes:
        f, gc, cur = heapq.heappop(open_h)
        if gc > g.get(cur, 1e18): continue
        h = manhattan(cur, goal)
        if h < bestd: bestd, best = h, cur
        if cur == goal or (closer(cur, goal, 1.8) and not v.standable(*goal)):
            return rebuild(came, start, cur), expanded
        expanded += 1
        for nb, step in neighbors(v, cur):
            t = gc + step + danger(*nb)
            if t < g.get(nb, 1e18):
                g[nb] = t; came[nb] = cur
                heapq.heappush(open_h, (t + manhattan(nb, goal), t, nb))
    if best != start:
        return rebuild(came, start, best), expanded
    nb = neighbors(v, start)
    return ([start, nb[0][0]] if nb else None), expanded


def rebuild(came, start, end):
    path = [end]
    while path[-1] != start and path[-1] in came:
        path.append(came[path[-1]])
    path.reverse(); return path


# ---------- visualisation: side cross-section at a given z (x →, y ↑) ----------
def render_side(v, path, z, xr, yr, start, goal):
    pset = {(p[0],p[1]) for p in path} if path else set()
    rows = []
    for y in range(yr[1], yr[0]-1, -1):
        line = []
        for x in range(xr[0], xr[1]+1):
            c = '#' if v.solid(x,y,z) else '.'
            if (x,y) == (start[0],start[1]): c = 'S'
            elif (x,y) == (goal[0],goal[1]):  c = 'G'
            elif (x,y) in pset:               c = 'o'
            line.append(c)
        rows.append(f"y={y:2d} " + ''.join(line))
    return '\n'.join(rows)


# ---------- visualisation: top-down at a given walk level y (x →, z ↓) ----------
def render_top(v, path, y, xr, zr, start, goal):
    pset = {(p[0],p[2]) for p in path} if path else set()
    rows = []
    for z in range(zr[0], zr[1]+1):
        line = []
        for x in range(xr[0], xr[1]+1):
            # solid if the cell at the walk level (or just below) blocks/holds here
            c = '#' if v.solid(x,y,z) else ('.' if v.solid(x,y-1,z) else ' ')
            if (x,z) == (start[0],start[2]): c = 'S'
            elif (x,z) == (goal[0],goal[2]):  c = 'G'
            elif (x,z) in pset:               c = 'o'
            line.append(c)
        rows.append(f"z={z:2d} " + ''.join(line))
    return '\n'.join(rows)


SCENARIOS = []
def scenario(fn): SCENARIOS.append(fn); return fn


def floor(s, x0,x1,z0,z1, y=0):
    for x in range(x0,x1+1):
        for z in range(z0,z1+1): s.add((x,y,z))


@scenario
def gaps():
    s=set(); floor(s,0,8,0,0)
    for x in (2,4,6): s.discard((x,0,0))
    return "consecutive 1-block gaps (jump each)", Voxel(s), (0,1,0),(8,1,0), 0,(0,8),(0,3)

@scenario
def pillar():
    s=set(); floor(s,-1,6,-1,1)
    for y in range(0,5): s.add((0,y,0))
    return "drop off a 5-high pillar", Voxel(s), (0,5,0),(6,1,0), 0,(-1,6),(0,6)

@scenario
def wall():
    s=set(); floor(s,-1,6,-5,6)
    for z in range(-4,5):
        s.add((3,1,z)); s.add((3,2,z))
    return "round a long 2-high wall (opening past z=4)", Voxel(s), (0,1,0),(6,1,0), 0,(-1,6),(0,3)

@scenario
def staircase():
    s=set(); floor(s,0,6,0,0)
    s.update({(1,1,0),(2,1,0),(2,2,0),(3,1,0),(3,2,0),(3,3,0)})
    return "climb a staircase", Voxel(s), (0,1,0),(3,4,0), 0,(0,6),(0,5)

@scenario
def islands():
    s=set()
    for x in (0,2,4,6,8): s.add((x,0,0))   # platforms separated by 1-block gaps (jump chain)
    return "floating-island jump chain (1-gaps)", Voxel(s),(0,1,0),(8,1,0),0,(0,8),(0,3)

@scenario
def gap2():
    s=set(); floor(s,0,6,0,0)
    s.discard((2,0,0)); s.discard((3,0,0))  # a 2-WIDE gap (stream/path) — can we jump it?
    return "2-WIDE gap (stream)", Voxel(s),(0,1,0),(6,1,0),0,(0,6),(0,3)

@scenario
def tunnel():
    s=set(); floor(s,0,8,0,0)
    for x in range(3,6):                      # a hill with a 2-high tunnel bored through it
        for y in range(3,6): s.add((x,y,0))
        s.add((x,0,0))
    return "low tunnel under a hill (head clearance)", Voxel(s),(0,1,0),(8,1,0),0,(0,8),(0,6)

@scenario
def ledge2():
    s=set(); floor(s,0,6,0,0)
    for y in (1,2): s.add((3,y,0))            # a 2-high ledge straight ahead (can't jump 2 — must it go round?)
    return "2-high ledge ahead (no side route)", Voxel(s),(0,1,0),(4,1,0),0,(0,6),(0,4)

@scenario
def fence():
    s=set(); floor(s,0,6,-1,3); tall=set()
    for z in (0,1,2):                         # a 1.5-high fence line at x=3 (z=0..2), open past z=2
        s.add((3,1,z)); tall.add((3,1,z))
    return "1.5-high fence — go AROUND, don't hop over", Voxel(s,tall),(0,1,0),(6,1,0),0,(0,6),(0,3)

@scenario
def diag_gap():
    s=set()
    s.update({(0,0,0),(1,0,1),(2,0,2),(3,0,3)})  # stepping stones on the DIAGONAL with 1-gaps between
    return "diagonal 1-gap stepping stones", Voxel(s),(0,1,0),(3,1,3),0,(0,3),(0,3)

@scenario
def maze():
    s=set(); floor(s,0,8,0,6)
    walls=[(2,1),(2,2),(2,3),(2,4),  (5,2),(5,3),(5,4),(5,5),(5,6)]
    for wx,wz in walls:
        s.add((wx,1,wz)); s.add((wx,2,wz))
    return "serpentine maze", Voxel(s),(0,1,0),(8,1,6),0,(0,8),(0,3)

@scenario
def pit():
    s=set(); floor(s,0,10,0,0)
    for x in range(4,7): s.discard((x,0,0))                  # a 3-wide, deep pit
    for x in range(4,7): s.add((x,-3,0))                     # pit floor 3 down
    return "deep pit (best-effort: can't climb sheer walls)", Voxel(s),(0,1,0),(10,1,0),0,(0,10),(-3,3)


if __name__ == "__main__":
    for fn in SCENARIOS:
        title, v, start, goal, z, xr, yr = fn()
        path, exp = find_path(v, start, goal)
        reached = path and path[-1] == goal
        partial = path and path[-1] != goal
        tag = "REACHED" if reached else ("PARTIAL" if partial else "NO-PATH")
        print(f"\n=== {title} ===  [{tag}]  nodes={exp}  len={len(path) if path else 0}")
        print(render_side(v, path, z, xr, yr, start, goal))
        if path and len({p[2] for p in path}) > 1:  # the route detours in z → show it top-down too
            zs = [p[2] for p in path]
            print("  top-down (x→, z↓):")
            print(render_top(v, path, start[1], xr, (min(zs)-1, max(zs)+1), start, goal))
