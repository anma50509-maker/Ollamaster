package com.ollamaster;

import android.content.Intent;
import android.view.View;

public abstract class Page {
    public final MainActivity act;
    public View root;

    public Page(MainActivity a) { act = a; }

    protected abstract View build();

    public View ensure() {
        if (root == null) root = build();
        return root;
    }

    public void onShow() {}
    public void onHide() {}
    public boolean onBack() { return false; }
    public void onHostChanged() {}
    public void onActivityResult(int req, int res, Intent data) {}
    public void onPermission(int req, String[] perms, int[] grants) {}
}
