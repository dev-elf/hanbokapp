package com.salmon.oneno.hanbok.search;

import java.util.List;

/**
 * Created by oneno on 2017-01-03.
 */

public interface OnFinishSearchListener {
    public void onSuccess(List<Item> itemList);
    public void onFail();
}