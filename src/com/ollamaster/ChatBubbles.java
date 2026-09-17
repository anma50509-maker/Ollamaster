package com.ollamaster;

import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * ChatPage 的消息气泡构建与列表适配器：用户气泡 / AI 气泡（思考折叠+富文本表格）/ 工具与通知小卡片。
 * 从 ChatPage 拆出（原 2843 行巨型文件）。气泡构建时向 ChatPage.streamViews 注册流式刷新句柄。
 */
class ChatBubbles {
    final ChatPage cp;
    final MainActivity act;

    ChatBubbles(ChatPage cp) {
        this.cp = cp;
        this.act = cp.act;
    }

    /** 虚拟化消息列表：ListView 只测量/布局屏幕内的气泡，历史长度不再拖慢流式刷新 */
    BaseAdapter adapter() {
        return new BaseAdapter() {
            @Override public int getCount() { return cp.conv == null ? 0 : cp.conv.msgs.size(); }
            @Override public Object getItem(int position) { return cp.conv.msgs.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public int getViewTypeCount() { return 3; }
            @Override public int getItemViewType(int position) {
                String r = cp.conv.msgs.get(position).role;
                if ("user".equals(r)) return 0;
                if ("assistant".equals(r)) return 1;
                return 2;
            }
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                final ConvStore.Msg m = cp.conv.msgs.get(position);
                try {
                    String r = m.role;
                    if ("user".equals(r)) return buildUserBubble(m);
                    if ("assistant".equals(r)) return buildAiBubble(m);
                    return buildSmallCard(m);
                } catch (Throwable e) {
                    TextView fb = new TextView(act);
                    fb.setText(m.content);
                    fb.setTextColor(cp.t.textPri);
                    fb.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.spi(act, 14.5f));
                    int pad = Ui.dpi(act, 10);
                    fb.setPadding(pad, pad, pad, pad);
                    return fb;
                }
            }
        };
    }

    private View buildUserBubble(final ConvStore.Msg m) {
        Theme t = cp.t;
        LinearLayout wrap = new LinearLayout(act);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(Gravity.END);

        boolean hasAtt = m.attaches != null && !m.attaches.isEmpty();
        if (hasAtt) {
            LinearLayout chips = new LinearLayout(act);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            chips.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            for (String p : m.attaches) {
                TextView chip = new TextView(act);
                chip.setText(ChatPage.attachLabel(p));
                Icon.pinLeft(chip, ChatPage.attachKind(p), 12);
                chip.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 10.5f));
                chip.setTextColor(t.textSec);
                chip.setSingleLine(true);
                int cpad = Ui.dpi(act, 7);
                chip.setPadding(cpad, Ui.dpi(act, 3), cpad, Ui.dpi(act, 3));
                chip.setBackground(Ui.round(t.alpha(t.textPri, 0.07f), Ui.dpi(act, 999)));
                chips.addView(chip);
                LinearLayout.LayoutParams clp = (LinearLayout.LayoutParams) chip.getLayoutParams();
                clp.leftMargin = Ui.dpi(act, 5);
                chip.setLayoutParams(clp);
            }
            wrap.addView(chips, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        TextView tv = new TextView(act);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.spi(act, 14.5f));
        tv.setLineSpacing(0, 1.25f);
        int pad = Ui.dpi(act, 13);
        tv.setPadding(pad, pad - 3, pad, pad - 3);
        tv.setTextColor(t.mixTextOn(t));
        tv.setText(m.content);
        tv.setOnLongClickListener(vv -> {
            cp.dialogs.msgMenu(m, false);
            return true;
        });
        tv.setHighlightColor(0x55FFFFFF);
        tv.setBackground(Ui.radii(t.alpha(t.accent, 0.92f), Ui.dpi(act, 17),
                Ui.dpi(act, 4), Ui.dpi(act, 17), Ui.dpi(act, 17)));
        tv.setMaxWidth(Ui.dpi(act, 272));
        row.addView(tv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrap.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) tv.getLayoutParams();
        lp.topMargin = Ui.dpi(act, 5);
        lp.bottomMargin = Ui.dpi(act, 5);
        tv.setLayoutParams(lp);

        // 时间戳行：同时提供长按消息菜单入口（文本选择模式已占用地板长按）
        TextView umeta = new TextView(act);
        umeta.setText(cp.tf.format(new Date(m.ts)));
        umeta.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 9.5f));
        umeta.setTextColor(t.alpha(t.textSec, 0.9f));
        umeta.setPadding(0, 0, Ui.dpi(act, 6), 0);
        umeta.setGravity(Gravity.END);
        umeta.setOnLongClickListener(vv -> {
            cp.dialogs.msgMenu(m, false);
            return true;
        });
        wrap.addView(umeta, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        wrap.setOnLongClickListener(vv -> {
            cp.dialogs.msgMenu(m, false);
            return true;
        });
        return wrap;
    }

    private View buildAiBubble(final ConvStore.Msg m) {
        Theme t = cp.t;
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);

        FrameLayout avatar = new FrameLayout(act);
        avatar.setBackground(Ui.stroke(t.alpha(t.accent, 0.12f), t.alpha(t.accent, 0.45f),
                Ui.dpi(act, 999), Ui.dpi(act, 0.9f)));
        ImageView avImg = new ImageView(act);
        Drawable avd = cp.persona != null ? cp.loadAvatar(cp.persona.avatar, 16) : null;
        avImg.setImageDrawable(avd != null ? avd : Icon.v(act, "avatar", t.accent, 14));
        avatar.addView(avImg, new FrameLayout.LayoutParams(Ui.dpi(act, 16), Ui.dpi(act, 16), Gravity.CENTER));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(Ui.dpi(act, 24), Ui.dpi(act, 24));
        alp.topMargin = Ui.dpi(act, 6);
        alp.rightMargin = Ui.dpi(act, 8);
        row.addView(avatar, alp);

        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView think = new TextView(act);
        think.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 11.5f));
        think.setLineSpacing(0, 1.2f);
        think.setTextColor(t.alpha(t.textPri, 0.55f));
        int tpad = Ui.dpi(act, 10);
        think.setPadding(tpad, tpad - 2, tpad, tpad - 2);
        think.setBackground(Ui.round(t.alpha(t.textPri, 0.05f), Ui.dpi(act, 10)));
        think.setVisibility(View.GONE);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.bottomMargin = Ui.dpi(act, 5);
        tlp.rightMargin = Ui.dpi(act, 34);
        col.addView(think, tlp);

        String raw = m.content == null ? "" : m.content;
        boolean showThink = Prefs.get(act).showThink();
        String thinkText = null, answerText;
        int ta = ChatPage.idxOf(raw, "<think>");
        if (showThink && ta >= 0) {
            int tb = ChatPage.idxOf(raw, "</think>", ta + 7);
            thinkText = tb >= 0 ? raw.substring(ta + 7, tb) : raw.substring(ta + 7);
            answerText = tb >= 0 ? raw.substring(tb + 8) : "";
        } else {
            answerText = ChatPage.stripThink(raw);
        }
        if (showThink && (thinkText == null || thinkText.trim().isEmpty())) {
            thinkText = m.reasoning;
        }
        if (thinkText != null && !thinkText.trim().isEmpty()) {
            applyThinkBlock(think, "think|" + m.ts, thinkText);
        }

        // 工具调用轮次：content 为空但有工具调用时，显示工具调用摘要，避免空消息观感
        if (answerText.trim().isEmpty() && m.tools != null && !m.tools.isEmpty()) {
            StringBuilder tsb = new StringBuilder();
            for (int ti = 0; ti < m.tools.size() && ti < 3; ti++) {
                if (ti > 0) tsb.append("、");
                tsb.append(m.tools.get(ti).name);
            }
            if (m.tools.size() > 3) tsb.append(" 等");
            answerText = "[调用工具 " + tsb + "]";
        }

        // 正文容器：统一圆角背景，内部按 flow 顺序渲染文本块与表格/图片视图（视图停留在原文位置）
        LinearLayout bodyBox = new LinearLayout(act);
        bodyBox.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dpi(act, 13);
        bodyBox.setPadding(pad, pad - 3, pad, pad - 3);
        bodyBox.setBackground(Ui.radii(t.surfaceAlt, Ui.dpi(act, 4), Ui.dpi(act, 17),
                Ui.dpi(act, 17), Ui.dpi(act, 17)));

        Markdown.RichResult rr;
        try {
            if (!cp.streaming) {
                // 完整答案：文本块与表格/图片按原文顺序穿插渲染
                rr = Markdown.prepareRich(act, answerText, t);
            } else {
                // 流式：实时 MD 渲染为单个文本块，后续由 refreshStreamingBubble 持续更新
                rr = new Markdown.RichResult();
                rr.flow.add(Markdown.render(act, answerText.isEmpty() ? "▍" : answerText, t));
            }
        } catch (Throwable e) {
            rr = new Markdown.RichResult();
            rr.flow.add(answerText);
        }

        TextView firstText = null;
        for (Object seg : rr.flow) {
            if (seg instanceof View) {
                LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                vlp.topMargin = Ui.dpi(act, 2);
                bodyBox.addView((View) seg, vlp);
            } else {
                TextView chunk = new TextView(act);
                chunk.setText((CharSequence) seg);
                chunk.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.spi(act, 14.5f));
                chunk.setLineSpacing(0, 1.3f);
                chunk.setHighlightColor(t.alpha(t.accent, 0.26f));
                chunk.setTextColor(t.textPri);
                chunk.setMaxWidth(Ui.dpi(act, 272));
                if (firstText == null) {
                    firstText = chunk;
                    bodyBox.addView(chunk, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                } else {
                    LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    clp.topMargin = Ui.dpi(act, 6);
                    bodyBox.addView(chunk, clp);
                }
            }
        }
        if (firstText == null) {
            firstText = new TextView(act);
            firstText.setText("");
            firstText.setVisibility(View.GONE);
            bodyBox.addView(firstText);
        }
        bodyBox.setVisibility(answerText.isEmpty() && !cp.streaming ? View.GONE : View.VISIBLE);
        bodyBox.setOnLongClickListener(vv -> {
            cp.dialogs.msgMenu(m, true);
            return true;
        });
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyLp.rightMargin = Ui.dpi(act, 34);
        col.addView(bodyBox, bodyLp);

        TextView meta = new TextView(act);
        StringBuilder mt = new StringBuilder(cp.tf.format(new Date(m.ts)));
        if (m.evalTokens > 0 && m.tps > 0) mt.append(" · ").append(m.evalTokens)
                .append(" tok · ").append(String.format(Locale.US, "%.1f tok/s", m.tps));
        mt.append(" · ").append(m.model != null && !m.model.isEmpty() ? m.model : cp.modelShort());
        if (m.promptTokens > 0) {
            mt.append(" · 输入 ").append(m.promptTokens);
            long hit = m.cacheHitTokens;
            long miss = m.cacheMissTokens;
            if (hit > 0) {
                long total = hit + miss;
                if (total <= 0) total = m.promptTokens;
                int rate = (int)(hit * 100 / total);
                mt.append(" · 缓存").append(rate).append("%");
            } else if (miss > 0) {
                mt.append(" · 缓存0%");
            }
        }
        meta.setText(mt);
        meta.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 9.5f));
        meta.setTextColor(t.alpha(t.textSec, 0.9f));
        meta.setPadding(Ui.dpi(act, 5), Ui.dpi(act, 3), Ui.dpi(act, 4), 0);
        meta.setOnLongClickListener(v -> {
            cp.dialogs.msgMenu(m, true);
            return true;
        });
        col.addView(meta);

        row.addView(col, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        row.setTag(new ChatPage.AiHolder(think, firstText, bodyBox));
        // 登记到流式刷新注册表：流式期间重建列表后仅重新绑定正在生成的气泡，避免误绑旧消息
        if (!cp.streaming || m == cp.streamMsg) cp.streamViews.put(System.identityHashCode(m), new ChatPage.AiHolder(think, firstText, bodyBox));
        if (cp.pendingRegister && m == cp.streamMsg) { cp.pendingRegister = false; cp.markDirty(); }
        row.setOnLongClickListener(vv -> {
            cp.dialogs.msgMenu(m, true);
            return true;
        });
        return row;
    }

    private static final java.util.HashSet<String> expandedCards = new java.util.HashSet<>();

    private View buildSmallCard(final ConvStore.Msg m) {
        Theme t = cp.t;
        LinearLayout wrap = new LinearLayout(act);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(Ui.dpi(act, 30), Ui.dpi(act, 3), Ui.dpi(act, 8), Ui.dpi(act, 3));

        boolean isTool = "tool".equals(m.role);
        final String key = m.ts + "|" + m.role + "|" + (m.toolName == null ? "" : m.toolName);
        TextView card = new TextView(act);
        card.setTextColor(isTool ? t.alpha(t.accent, 0.95f) : t.alpha(t.danger, 0.95f));
        card.setTypeface(isTool ? Ui.mono() : Typeface.DEFAULT);
        card.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 11));
        card.setBackground(Ui.round(t.alpha(isTool ? t.accent : t.danger, 0.07f), Ui.dpi(act, 10)));
        int cpad = Ui.dpi(act, 9);
        card.setPadding(cpad, cpad - 3, cpad, cpad - 3);

        Runnable apply = () -> {
            boolean expanded = expandedCards.contains(key);
            if (isTool && !expanded) {
                String head = m.content.replace('\n', ' ').trim();
                if (head.length() > 60) head = head.substring(0, 60) + "…";
                card.setText("[" + m.toolName + "] " + head);
                card.setMaxLines(1);
            } else {
                card.setText((isTool ? "[" + m.toolName + "]\n" : "") + m.content);
                card.setMaxLines(Integer.MAX_VALUE);
            }
        };
        apply.run();
        wrap.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (isTool) wrap.setOnClickListener(v -> {
            if (!expandedCards.remove(key)) expandedCards.add(key);
            apply.run();
        });
        wrap.setOnLongClickListener(vv -> {
            cp.dialogs.msgMenu(m, false);
            return true;
        });
        return wrap;
    }


    /** 思考折叠条：收起为一行摘要，点击展开/收起全文（半透明小字号） */
    void applyThinkBlock(TextView think, String key, String thinkText) {
        boolean expanded = expandedCards.contains(key);
        Icon.unpin(think);
        if (expanded) {
            think.setText("已深度思考\n" + thinkText.trim());
            think.setMaxLines(500);
        } else {
            think.setText("已深度思考");
            think.setMaxLines(1);
        }
        think.setVisibility(View.VISIBLE);
        Icon.pinLeft(think, "think", 13);
        think.setOnClickListener(v -> {
            if (expandedCards.contains(key)) expandedCards.remove(key);
            else expandedCards.add(key);
            applyThinkBlock(think, key, thinkText);
        });
    }
}
