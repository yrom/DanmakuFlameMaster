/*
 * Copyright (C) 2013 Chen Hui <calmer91@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package master.flame.danmaku.danmaku.renderer.android;

import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.IDisplayer;
import master.flame.danmaku.danmaku.model.android.Danmakus;
import master.flame.danmaku.danmaku.util.DanmakuUtils;

public class DanmakusRetainer {

    enum Retainer {
        RL(BaseDanmaku.TYPE_SCROLL_RL) {
            @Override
            public void fix(BaseDanmaku danmaku, IDisplayer disp) {
                if (rldrInstance == null) {
                    rldrInstance = new RLDanmakusRetainer();
                }
                rldrInstance.fix(danmaku, disp);
            }
        },
        LR(BaseDanmaku.TYPE_SCROLL_LR) {
            @Override
            public void fix(BaseDanmaku danmaku, IDisplayer disp) {
                if (lrdrInstance == null) {
                    lrdrInstance = new RLDanmakusRetainer();
                }
                lrdrInstance.fix(danmaku, disp);
            }
        },
        FT(BaseDanmaku.TYPE_FIX_TOP) {
            @Override
            public void fix(BaseDanmaku danmaku, IDisplayer disp) {
                if (ftdrInstance == null) {
                    ftdrInstance = new FTDanmakusRetainer();
                }
                ftdrInstance.fix(danmaku, disp);
            }
        },
        FB(BaseDanmaku.TYPE_FIX_BOTTOM) {
            @Override
            public void fix(BaseDanmaku danmaku, IDisplayer disp) {
                if (fbdrInstance == null) {
                    fbdrInstance = new FBDanmakusRetainer();
                }
                fbdrInstance.fix(danmaku, disp);
            }
        },
        S(BaseDanmaku.TYPE_SPECIAL) {
            @Override
            public void fix(BaseDanmaku danmaku, IDisplayer disp) {
                danmaku.layout(disp, 0, 0);
            }
        },
        UNKNOWN(BaseDanmaku.TYPE_MOVEABLE_XXX) {
            @Override
            public void fix(BaseDanmaku danmaku, IDisplayer disp) {
                // nothing
            }
        };
        int mType;

        Retainer(int type) {
            mType = type;
        }

        static Retainer get(int type) {
            for (Retainer instance : values()) {
                if (instance.mType == type)
                    return instance;
            }
            return UNKNOWN;
        }

        public abstract void fix(BaseDanmaku danmaku, IDisplayer disp);
    }
    
    private static IDanmakusRetainer rldrInstance;
    private static IDanmakusRetainer lrdrInstance;
    private static IDanmakusRetainer ftdrInstance;
    private static IDanmakusRetainer fbdrInstance;

    public static void fix(BaseDanmaku danmaku, IDisplayer disp) {
        int type = danmaku.getType();
        Retainer.get(type).fix(danmaku, disp);
    }

    public static void clear() {
        if (rldrInstance != null) {
            rldrInstance.clear();
        }
        if (lrdrInstance != null) {
            lrdrInstance.clear();
        }
        if (ftdrInstance != null) {
            ftdrInstance.clear();
        }
        if (fbdrInstance != null) {
            fbdrInstance.clear();
        }
    }
    
    public static void release(){
        clear();
        rldrInstance = null;
        lrdrInstance = null;
        ftdrInstance = null;
        fbdrInstance = null;
    }

    public interface IDanmakusRetainer {
        public void fix(BaseDanmaku drawItem, IDisplayer disp);

        public void clear();

    }

    private static class RLDanmakusRetainer implements IDanmakusRetainer {

        protected Danmakus mVisibleDanmakus = new Danmakus(Danmakus.ST_BY_YPOS);

        @Override
        public void fix(BaseDanmaku drawItem, IDisplayer disp) {
            if (drawItem.isOutside())
                return;
            float topPos = 0;
            boolean shown = drawItem.isShown();
            if (!shown) {
                // 确定弹幕位置
                BaseDanmaku insertItem = null, firstItem = null, lastItem = null, minRightRow = null;
                boolean overwriteInsert = false;
                for(BaseDanmaku item : mVisibleDanmakus){
                    if(item == drawItem){
                        insertItem = item;
                        lastItem = null;
                        shown = true;
                        break;
                    }
                    
                    if (firstItem == null)
                        firstItem = item;

                    if (drawItem.paintHeight + item.getTop() > disp.getHeight()) {
                        overwriteInsert = true;
                        break;
                    }

                    if (minRightRow == null || minRightRow.getRight() >= item.getRight()) {
                        minRightRow = item;
                    }

                    // 检查碰撞
                    boolean willHit = DanmakuUtils.willHitInDuration(disp, item, drawItem,
                            drawItem.getDuration(), drawItem.getTimer().currMillisecond);
                    if (!willHit) {
                        insertItem = item;
                        break;
                    }

                    lastItem = item;

                }

                if (insertItem != null) {
                    topPos = lastItem != null ? lastItem.getBottom() : insertItem.getTop();
                    if (insertItem != drawItem){
                        mVisibleDanmakus.removeItem(insertItem);
                        shown = false;
                    }
                } else if (overwriteInsert) {
                    if (minRightRow != null) {
                        topPos = minRightRow.getTop();
                        if(minRightRow.paintWidth<drawItem.paintWidth){
                            mVisibleDanmakus.removeItem(minRightRow);
                            shown = false;
                        }
                    }
                } else if (lastItem != null) {
                    topPos = lastItem.getBottom();
                } else if (firstItem != null) {
                    topPos = firstItem.getTop();
                    mVisibleDanmakus.removeItem(firstItem);
                    shown = false;
                } else {
                    topPos = 0;
                }

                topPos = checkVerticalEdge(overwriteInsert, drawItem, disp, topPos, firstItem,
                        lastItem);
                if (topPos == 0 && mVisibleDanmakus.size()==0) {
                    shown = false;
                }
            }

            drawItem.layout(disp, drawItem.getLeft(), topPos);

            if (!shown) {
                mVisibleDanmakus.addItem(drawItem);
            }

        }

        protected float checkVerticalEdge(boolean overwriteInsert, BaseDanmaku drawItem,
                IDisplayer disp, float topPos, BaseDanmaku firstItem, BaseDanmaku lastItem) {
            if (topPos < 0 || (firstItem!=null && firstItem.getTop() > 0) || topPos + drawItem.paintHeight > disp.getHeight()) {
                topPos = 0;
                clear();
            }
            return topPos;
        }

        @Override
        public void clear() {
            mVisibleDanmakus.clear();
        }

    }

    private static class FTDanmakusRetainer extends RLDanmakusRetainer {

        @Override
        protected float checkVerticalEdge(boolean overwriteInsert, BaseDanmaku drawItem,
                IDisplayer disp, float topPos, BaseDanmaku firstItem, BaseDanmaku lastItem) {
            if (topPos + drawItem.paintHeight > disp.getHeight()) {
                topPos = 0;
                clear();
            }
            return topPos;
        }

    }

    private static class FBDanmakusRetainer extends FTDanmakusRetainer {

        protected Danmakus mVisibleDanmakus = new Danmakus(Danmakus.ST_BY_YPOS_DESC);

        @Override
        public void fix(BaseDanmaku drawItem, IDisplayer disp) {
            if (drawItem.isOutside())
                return;
            boolean shown = drawItem.isShown();
            float topPos = drawItem.getTop();
            if (topPos < 0) {
                topPos = disp.getHeight() - drawItem.paintHeight;
            }
            BaseDanmaku removeItem = null, firstItem = null;
            if (!shown) {
                for(BaseDanmaku item : mVisibleDanmakus){
                    if (item == drawItem) {
                        removeItem = null;
                        break;
                    }

                    if (firstItem == null) {
                        firstItem = item;
                        if (firstItem.getBottom() != disp.getHeight()) {
                            break;
                        }
                    }

                    if (topPos < 0) {
                        removeItem = null;
                        break;
                    }

                    // 检查碰撞
                    boolean willHit = DanmakuUtils.willHitInDuration(disp, item, drawItem,
                            drawItem.getDuration(), drawItem.getTimer().currMillisecond);
                    if (!willHit) {
                        removeItem = item;
                        // topPos = item.getBottom() - drawItem.paintHeight;
                        break;
                    }

                    topPos = item.getTop() - drawItem.paintHeight;

                }

                topPos = checkVerticalEdge(false, drawItem, disp, topPos, firstItem, null);

            }

            drawItem.layout(disp, drawItem.getLeft(), topPos);

            if (!shown) {
                mVisibleDanmakus.removeItem(removeItem);
                mVisibleDanmakus.addItem(drawItem);
            }

        }

        protected float checkVerticalEdge(boolean overwriteInsert, BaseDanmaku drawItem,
                IDisplayer disp, float topPos, BaseDanmaku firstItem, BaseDanmaku lastItem) {
            if (topPos < 0 || (firstItem != null && firstItem.getBottom() != disp.getHeight())) {
                topPos = disp.getHeight() - drawItem.paintHeight;
                clear();
            }
            return topPos;
        }

        @Override
        public void clear() {
            mVisibleDanmakus.clear();
        }

    }

}
