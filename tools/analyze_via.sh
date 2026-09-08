#!/usr/bin/env bash
# =====================================================================
# analyze_via.sh — Via 新版 UI 搜索增强 hook 点自动适配分析器
# ---------------------------------------------------------------------
# 用法:  ./tools/analyze_via.sh <新版via.apk>
#
# 作用: 输入新 Via APK，自动找出 UiSearchEnhance.PageSpec 全部符号。
#   自动产出: 每个 toolbar 列表页候选的
#     - Fragment 类、toolbar 回调名(Y2)、onViewCreated(V1)
#     - 列表构建方法(build)、列表写入方法(apply)
#     - adapter 注册的条目类型/header 类型
#   人工: 从候选表中挑出 规则订阅/脚本 两行（新版页数不变，页面对号即可）
#   依据: 知识库 api102开发实战.md §23
# =====================================================================
set -uo pipefail
[ $# -lt 1 ] && { echo "用法: $0 <Via.apk>" >&2; exit 2; }
APK="$1"; [ -f "$APK" ] || { echo "找不到 APK: $APK" >&2; exit 2; }
WORK="$(mktemp -d /tmp/via_analyze.XXXXXX)"; trap 'rm -rf "$WORK"' EXIT
TOOLBAR="com/tuyafeng/support/widget/z"

echo "== [1/5] 解 dex =="
unzip -o -q "$APK" "classes*.dex" -d "$WORK/dex" || exit 1
DEX=$(ls "$WORK/dex"/classes*.dex 2>/dev/null | head -1)
[ -n "$DEX" ] || { echo "APK 无 dex" >&2; exit 1; }
echo "    $(basename "$DEX") ($(du -h "$DEX"|cut -f1))"

echo "== [2/5] baksmali (~20s) =="
baksmali d "$DEX" -o "$WORK/smali" --use-locals || exit 1
S="$WORK/smali"
[ -f "$S/$TOOLBAR.smali" ] || { echo "❌ 无工具栏库类 $TOOLBAR" >&2; exit 1; }
echo "    类数 $(find "$S" -name '*.smali'|wc -l)"

echo "== [3/5] 找 toolbar 列表页候选 =="
CAND=$(grep -rl "L$TOOLBAR;->c(L$TOOLBAR\$b;" "$S" --include="*.smali")
echo "    含 z.c(加按钮) 的类: $(echo "$CAND"|grep -c .)"

echo "== [4/5] 逐候选解析结构符号 =="
python3 - "$S" <<'PYEOF'
import sys,re,os
S=sys.argv[1]
import subprocess
cands=[]
# grep -l 用单引号模式(防 shell 展开 $b)
hits=subprocess.check_output(['grep','-rl','Lcom/tuyafeng/support/widget/z;->c(Lcom/tuyafeng/support/widget/z$b;',S,'--include=*.smali'],text=True).split()
for f in hits:
    rel=f[len(S)+1:-6]
    txt=open(f,encoding='utf-8',errors='replace').read()
    if ('Landroid/view/View;Landroid/os/Bundle;' in txt and 'r0:Ljava/util/List;' in txt):
        cands.append((rel,txt))
print(f"    列表页候选(含 z.c + V1 + r0:List 数据源): {len(cands)}")

def blocks(txt):
    return re.split(r'(?=\.method )',txt)

def info(rel,txt):
    y2=v1=build=apply=None
    v1body=None
    for b in blocks(txt):
        m=re.search(r'\.method (?:(?:public|private|protected)(?: final| static| final static| static final)*|static) ([\w$]+)\(([^)]*)\)',b)
        if not m: continue
        n,args=m.group(1),m.group(2)
        if n in('<init>','<clinit>'): continue
        if n=='Y2' and 'Lcom/tuyafeng/support/widget/z;' in args: y2=n
        if n=='V1' and 'Landroid/view/View;' in args and 'Landroid/os/Bundle;' in args: v1=n; v1body=b
        # build: 无参且返回 Ljava/util/List; 且方法体读取 r0 数据源
        if args=='' and re.search(r'\)Ljava/util/List;',b.split(chr(10))[0]) and '->r0:Ljava/util/List;' in b and build is None:
            if 'new-instance' in b or 'addAll' in b or 'iterator' in b: build=n
        if args=='Ljava/util/List;' and ('widget/f;->b' in b or 'DiffUtil' in b) and apply is None: apply=n
    regs=[]
    if v1body:
        # 每个 K() 调用回溯其寄存器最近的 const-class = 注册的展示类型(header/条目)
        for km in re.finditer(r'invoke-\w+ \{[^}]*v(\d+)[^}]*\}, Ly5/f;->K\(',v1body):
            reg=km.group(1)
            before=v1body[:km.start()]
            cms=list(re.finditer(r'const-class v'+reg+r', (L[^;]+);',before))
            if cms and cms[-1].group(1) not in regs: regs.append(cms[-1].group(1))
    return y2,v1,build,apply,regs

found=0
for rel,txt in cands:
    y2,v1,build,apply,regs=info(rel,txt)
    if not build and not apply: continue
    found+=1
    print(f"\n### [{found}] Fragment: {rel}")
    print(f"    toolbar回调: {y2}    onViewCreated: {v1}")
    print(f"    build(列表构建, 0参返List): {build}")
    print(f"    apply(列表写入, 1参List):   {apply}")
    if regs:
        print(f"    adapter 注册类型(V1中 K()): {', '.join(regs)}")
        # 提示: 区分 header(a6/x 系列)与业务条目; 业务条目 binder 在下一个 const-class 后
        biz=[x for x in regs if not re.match(r'La6/',x)]
        if biz: print(f"    → 疑似业务条目类型: {', '.join(biz)} (读其 binder bind 方法确认显示名 getter)")
    # 提示条目类型: header 是 a6/x 或 La6/, 业务条目在 build 里被遍历 new
    for b in blocks(txt):
        m=re.search(r'\.method (?:public|private|protected|static|final) '+re.escape(build or '\b')+r'\(\)',b)
        if m:
            news=[x for x in set(re.findall(r'new-instance \w+, (L[^;]+);',b)) if re.match(r'L(z7|sa|hb|w4|r5|a6)/',x)]
            print(f"    build() 内 new 的候选条目类: {news}")
            break
print("\n== [5/5] 生成 PageSpec 草稿 ==")
print("从上面候选中挑出 F1 规则订阅 / F2 脚本 两页(页数不变, 按条目类型区分: 脚本页条目=Lsa/e1 型带启用开关)。")
print("'名称链(nameGetters)'需人工 30 秒确认: 读条目类对应 binder 的 bind 方法(如 sa/h1.m / Lib/g.l)")
print("找 setText(某getter()返回) 的那个 getter; 链式条目(如 sa.e1->r5.c)取每层。历史实锤: w4.c.d() / sa.e1.a()→r5.c.g()。")
# ---- onDestroyView 基类检测: 从任一候选沿 super 链找"M1 式"覆写基类 ----
print("\n== [6/6] onDestroyView 基类 hook 点 ==")
def super_of(cls):
    """返回 cls(相对路径如 z7/q0) 的父类相对路径, 无则 None"""
    p2=os.path.join(S, cls + '.smali')
    if not os.path.exists(p2): return None
    t = open(p2, encoding='utf-8', errors='replace').read()
    m = re.search(r'^\.super (L([^;]+));', t, re.M)
    return m.group(2) if m else None

def find_m1(cls, depth=0):
    """沿 super 链找 super 直接调 androidx Fragment.M1 的类(即页面基类 onDestroyView 定义处)"""
    if depth > 8 or not cls: return None
    p2 = os.path.join(S, cls + '.smali')
    if not os.path.exists(p2): return None
    t = open(p2, encoding='utf-8', errors='replace').read()
    for b in blocks(t):
        mm = re.search(r'\.method (?:public|private|protected)(?: final)? (M1)\(\)V', b)
        if mm and 'Landroidx/fragment/app/Fragment;->M1' in b:
            return cls
    return find_m1(super_of(cls), depth + 1)

for rel, _ in cands:
    base = find_m1(rel)
    if base:
        print(f"    onDestroyView hook 基类 = {base}.M1() (super 直接到 androidx Fragment.M1)")
        break
print("\n--- 粘贴模板(把 ? 换成确认的 getter) ---")
PYEOF
