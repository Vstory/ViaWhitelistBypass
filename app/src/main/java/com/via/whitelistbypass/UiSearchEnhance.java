package com.via.whitelistbypass;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedInterface;

























public class UiSearchEnhance {

    private static final String TAG = "ViaTune";



    private static final String CLS_TOOLBAR = "com.tuyafeng.support.widget.z";

    private static final String CLS_TOOLBAR_BTN = "com.tuyafeng.support.widget.z$b";

    private static final String CLS_ICON_LOADER = "lb.b";

    private static final String CLS_R_DRAW = "x7.o";
    private static final String CLS_R_STR = "x7.u";

    private static final String M_TOOLBAR_CB = "Y2";

    private static final String CLS_FRAG_BASE = "o8.a";

    private static final String M_ON_DESTROY_VIEW = "M1";


    private static final String R_DRAW_ICON = "j1";
    private static final String R_STR_ICON = "jf";
    private static final String R_STR_SEARCH = "Mb";




    private static final class PageSpec {
        final String fragCls;
        final String buildMethod;
        final String applyMethod;
        final String itemType;
        final String[] nameGetters;
        final String dialogTitle;
        PageSpec(String fragCls, String build, String apply, String itemType,
                 String[] nameGetters, String dialogTitle) {
            this.fragCls = fragCls; this.buildMethod = build; this.applyMethod = apply;
            this.itemType = itemType; this.nameGetters = nameGetters; this.dialogTitle = dialogTitle;
        }
    }

    private static final PageSpec[] PAGE_SPECS = {

            new PageSpec("z7.q0", "y3", "H3", "w4.c", new String[]{"d"}, "搜索订阅源"),


            new PageSpec("sa.d1", "V3", "r4", "sa.e1", new String[]{"a", "g"}, "搜索脚本"),
    };


    private final WeakHashMap<Object, String> mKeywords = new WeakHashMap<>();

    private final XposedInterface mApi;
    private final ClassLoader mHostCl;

    public UiSearchEnhance(XposedInterface api, ClassLoader hostCl) {
        mApi = api;
        mHostCl = hostCl;
    }


    public int install() {
        int n = hookClearOnDestroyBase();
        for (PageSpec s : PAGE_SPECS) {
            n += hookY2Inject(s);
            n += hookListFilter(s);
        }
        return n;
    }

    private PageSpec specFor(Object frag) {
        String cn = frag.getClass().getName();
        for (PageSpec s : PAGE_SPECS) {
            if (s.fragCls.equals(cn)) return s;
        }
        return null;
    }




    private int hookClearOnDestroyBase() {
        try {
            Class<?> base = Class.forName(CLS_FRAG_BASE, false, mHostCl);
            Method m = base.getMethod(M_ON_DESTROY_VIEW);
            mApi.hook(m).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept((XposedInterface.Chain chain) -> {
                        Object fragInst = chain.getThisObject();
                        try {
                            return chain.proceed();
                        } finally {
                            if (mKeywords.remove(fragInst) != null) {
                                logD("page closed, search cleared for " + fragInst.getClass().getName());
                            }
                        }
                    });
            return 1;
        } catch (Throwable ignored) {
            return 0;
        }
    }



    private int hookY2Inject(PageSpec spec) {
        int count = 0;
        try {
            Class<?> frag = Class.forName(spec.fragCls, false, mHostCl);
            for (Method m : frag.getDeclaredMethods()) {
                if (m.getName().equals(M_TOOLBAR_CB) && m.getParameterTypes().length == 1) {
                    mApi.hook(m).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept((XposedInterface.Chain chain) -> {
                                logD("Y2 hook hit on " + spec.fragCls + " (toolbar arg present)");
                                Object fragInst = chain.getThisObject();
                                Object toolbar = chain.getArg(0);
                                try {
                                    injectSearchButton(fragInst, toolbar, spec);
                                } catch (Throwable t) {
                                    logE("inject search btn failed: " + t, t);
                                }
                                return chain.proceed();
                            });
                    count++;
                }
            }
        } catch (Throwable ignored) {

        }
        return count;
    }



    private int hookListFilter(PageSpec spec) {
        int count = 0;
        try {
            Class<?> frag = Class.forName(spec.fragCls, false, mHostCl);
            for (Method m : frag.getDeclaredMethods()) {
                if (m.getName().equals(spec.buildMethod) && m.getParameterTypes().length == 0) {
                    mApi.hook(m).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept((XposedInterface.Chain chain) -> {
                                Object result = chain.proceed();
                                Object fragInst = chain.getThisObject();
                                String kw = mKeywords.get(fragInst);
                                if (kw == null || result == null) return result;
                                try {
                                    return filterList(result, kw, spec);
                                } catch (Throwable t) {
                                    logE("filter list failed: " + t, t);
                                    return result;
                                }
                            });
                    count++;
                }
            }
        } catch (Throwable ignored) {
        }
        return count;
    }


    @SuppressWarnings("unchecked")
    private List<Object> filterList(Object result, String kw, PageSpec spec) throws Throwable {
        if (!(result instanceof List)) return (List<Object>) result;
        List<Object> src = (List<Object>) result;
        List<Object> out = new ArrayList<>();
        Method[] chain = null;
        int kept = 0, dropped = 0, nonItem = 0, nameNull = 0;
        for (Object item : src) {
            if (item == null) continue;
            if (spec.itemType.equals(item.getClass().getName())) {
                if (chain == null) chain = resolveNameChain(item.getClass(), spec);
                Object name = readName(item, chain);
                if (name == null) {
                    nameNull++;
                    continue;
                }
                if (!PinyinUtil.matches(name.toString(), kw)) {
                    dropped++;
                    continue;
                }
                kept++;
            } else {
                nonItem++;
            }
            out.add(item);
        }
        logD("filter " + spec.fragCls + " kw=\"" + kw + "\" " + src.size() + "→" + out.size()
                + " (kept " + kept + ", dropped " + dropped + ", nonItem " + nonItem + ", nameNull " + nameNull + ")");
        return out;
    }

    private Method[] resolveNameChain(Class<?> itemCls, PageSpec spec) throws NoSuchMethodException {
        Method[] ms = new Method[spec.nameGetters.length];
        Class<?> cur = itemCls;
        for (int i = 0; i < spec.nameGetters.length; i++) {
            ms[i] = cur.getMethod(spec.nameGetters[i]);
            ms[i].setAccessible(true);
            cur = ms[i].getReturnType();
        }
        return ms;
    }

    private static Object readName(Object item, Method[] chain) throws Throwable {
        Object cur = item;
        for (Method m : chain) {
            try {
                cur = m.invoke(cur);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
            if (cur == null) return null;
        }
        return cur;
    }



    private void injectSearchButton(Object frag, Object toolbar, PageSpec spec) throws Throwable {
        logD("inject: ctx...");
        final Context ctx = viewContext(toolbar);
        int iconRes = staticInt(CLS_R_DRAW, R_DRAW_ICON);
        int iconStr = staticInt(CLS_R_STR, R_STR_ICON);
        logD("inject: drawable res " + iconRes + " / icon str res " + iconStr);
        Object drawable = invokeStatic(CLS_ICON_LOADER, "a",
                new Class[]{Context.class, int.class, int.class}, ctx, iconRes, iconStr);
        logD("inject: drawable=" + drawable);
        if (drawable == null) return;
        int searchStr = staticInt(CLS_R_STR, R_STR_SEARCH);
        String label = ctx.getString(searchStr);
        logD("inject: label=" + label);
        Object btnBuilder = invokeStatic(CLS_TOOLBAR_BTN, "a",
                new Class[]{android.graphics.drawable.Drawable.class, String.class}, drawable, label);
        logD("inject: btnBuilder=" + btnBuilder);
        if (btnBuilder == null) return;
        final String title = spec.dialogTitle;
        Object listener = Proxy.newProxyInstance(mHostCl,
                new Class[]{View.OnClickListener.class},
                (Object proxy, Method method, Object[] args) -> {
                    if (method.getName().equals("onClick")) {
                        showSearchDialog(ctx, frag, title);
                        return null;
                    }
                    if (method.getName().equals("toString")) return "ViaTuneSearchListener";
                    return null;
                });
        logD("inject: listener=" + listener);
        Class<?> tb = Class.forName(CLS_TOOLBAR, false, mHostCl);
        Method c = tb.getMethod("c", Class.forName(CLS_TOOLBAR_BTN, false, mHostCl), View.OnClickListener.class);
        logD("inject: calling toolbar.c");
        c.invoke(toolbar, btnBuilder, listener);
        logD("search button injected on " + frag.getClass().getName());
    }

    private void showSearchDialog(Context ctx, Object frag, String title) {
        Runnable r = () -> {
            try {
                Activity act = resolveActivity(ctx);
                if (act == null || act.isFinishing()) return;
                EditText et = new EditText(act);
                et.setInputType(InputType.TYPE_CLASS_TEXT);
                et.setHint("输入名称关键词（留空=全部）");
                et.setSingleLine(true);
                String kw = mKeywords.get(frag);
                if (kw != null) et.setText(kw);
                AlertDialog dlg = new AlertDialog.Builder(act)
                        .setTitle(title)
                        .setView(et)
                        .setPositiveButton("确定", (d, w) -> applyKeyword(frag, et.getText().toString().trim()))
                        .setNeutralButton("清空", null)
                        .setNegativeButton("取消", null)
                        .create();
                dlg.setOnShowListener(d -> {

                    dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                        et.setText("");
                        applyKeyword(frag, "");
                    });
                });
                dlg.show();
            } catch (Throwable t) {
                logE("dialog failed: " + t, t);
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else new android.os.Handler(Looper.getMainLooper()).post(r);
    }


    private static Activity resolveActivity(Context ctx) {
        while (ctx != null) {
            if (ctx instanceof Activity) return (Activity) ctx;
            if (ctx instanceof android.content.ContextWrapper) {
                ctx = ((android.content.ContextWrapper) ctx).getBaseContext();
            } else {
                return null;
            }
        }
        return null;
    }


    private void applyKeyword(Object frag, String kw) {
        String key = kw.isEmpty() ? null : kw;
        if (key == null) mKeywords.remove(frag);
        else mKeywords.put(frag, key);
        logD("apply keyword[" + frag.getClass().getName() + "] = " + (key == null ? "<all>" : key));
        PageSpec spec = specFor(frag);
        if (spec == null) return;
        try {
            Class<?> c = frag.getClass();
            Method build = c.getDeclaredMethod(spec.buildMethod);
            build.setAccessible(true);
            Object list = build.invoke(frag);
            int n = (list instanceof List) ? ((List<?>) list).size() : -1;
            logD("refresh: " + spec.buildMethod + "() returned " + n + " rows");
            Method apply = c.getDeclaredMethod(spec.applyMethod, List.class);
            apply.setAccessible(true);
            apply.invoke(frag, list);
            logD("refresh: " + spec.applyMethod + "(list) applied OK");
        } catch (Throwable t) {
            logE("refresh list failed: " + t, t);
        }
    }



    private void logD(String msg) {
        if (BuildConfig.DEBUG) {
            mApi.log(Log.DEBUG, TAG, msg);
            Log.d(TAG, msg);
        }
    }

    private void logE(String msg) {
        mApi.log(Log.ERROR, TAG, msg);
        Log.e(TAG, msg);
    }

    private void logE(String msg, Throwable tr) {
        mApi.log(Log.ERROR, TAG, msg, tr);
        Log.e(TAG, msg, tr);
    }



    private static Context viewContext(Object view) throws Throwable {
        Method g = view.getClass().getMethod("getContext");
        return (Context) g.invoke(view);
    }

    private int staticInt(String clsName, String fieldName) throws Throwable {
        Class<?> c = Class.forName(clsName, false, mHostCl);
        Field f = c.getField(fieldName);
        return f.getInt(null);
    }

    private Object invokeStatic(String clsName, String methodName, Class<?>[] types, Object... args) throws Throwable {
        try {
            Class<?> c = Class.forName(clsName, false, mHostCl);
            Method m = c.getMethod(methodName, types);
            return m.invoke(null, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
