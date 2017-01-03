package tv.baokan.baokanandroid.cache;

import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.litepal.crud.DataSupport;
import org.litepal.tablemanager.Connector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import okhttp3.Call;
import tv.baokan.baokanandroid.model.ArticleListBean;
import tv.baokan.baokanandroid.utils.APIs;
import tv.baokan.baokanandroid.utils.LogUtils;
import tv.baokan.baokanandroid.utils.NetworkUtils;
import tv.baokan.baokanandroid.utils.ProgressHUD;

/**
 * 资讯数据访问层
 */

public class NewsDALManager {

    public static interface NewsListCallback {
        // 成功加载到数据
        public abstract void onSuccess(JSONArray jsonArray);

        // 加载数据失败
        public abstract void onError(String tipString);
    }

    private static final String TAG = "NewsDALManager";

    public static final NewsDALManager shared = new NewsDALManager();
    // 数据库
    private SQLiteDatabase db;

    private NewsDALManager() {
        // 创建数据库
        db = Connector.getDatabase();
    }

    /**
     * 加载资讯列表数据
     *
     * @param table     数据表 news photo
     * @param classid   分类id
     * @param pageIndex 分页页码
     */
    public void loadNewsList(final String table, final String classid, final int pageIndex, final NewsListCallback newsListCallback) {

        // 先从本地加载数据
        loadNewsListFromLocal(classid, pageIndex, new NewsListCallback() {

            // 数据加载成功
            @Override
            public void onSuccess(JSONArray jsonArray) {
                // 本地加载数据成功，就直接返回数据
                newsListCallback.onSuccess(jsonArray);
            }

            // 数据加载失败
            @Override
            public void onError(String tipString) {
                LogUtils.d(TAG, "加载本地数据 = " + tipString);
                // 从本地加载数据失败，就去网络加载
                loadNewsListFromNetwork(table, classid, pageIndex, new NewsListCallback() {
                    @Override
                    public void onSuccess(JSONArray jsonArray) {
                        // 加载到数据先缓存到本地
                        saveNewsList(classid, jsonArray);

                        // 然后才返回给调用者
                        newsListCallback.onSuccess(jsonArray);
                    }

                    @Override
                    public void onError(String tipString) {
                        newsListCallback.onError(tipString);
                    }
                });
            }
        });

    }

    /**
     * 缓存资讯列表数据到本地数据库
     *
     * @param classid   分类id
     * @param jsonArray 资讯json数据
     */
    private void saveNewsList(String classid, JSONArray jsonArray) {

        for (int i = 0; i < jsonArray.length(); i++) {
            try {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                if (classid.equals("0")) {
                    // 今日头条 分类
                    NewsListHomeCache homeCache = new NewsListHomeCache();
                    homeCache.setClassid(jsonObject.getString("classid"));
                    homeCache.setNews(jsonObject.toString());
                    homeCache.save();
                } else {
                    NewsListOtherCache otherCache = new NewsListOtherCache();
                    otherCache.setClassid(jsonObject.getString("classid"));
                    otherCache.setNews(jsonObject.toString());
                    otherCache.save();
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * 从本地缓存加载资讯数据列表
     *
     * @param classid   分类id
     * @param pageIndex 分页页码
     */
    private void loadNewsListFromLocal(String classid, int pageIndex, final NewsListCallback newsListCallback) {

        int preCount = (pageIndex - 1) * 20;
        int oneCount = 20;

        LogUtils.d(TAG, "当前分页 = " + pageIndex);

        JSONArray jsonArray = new JSONArray();

        if (classid.equals("0")) {
            // 今日头条 分类
            List<NewsListHomeCache> homeCaches = DataSupport.order("id asc").limit(oneCount).offset(preCount).find(NewsListHomeCache.class);
            for (NewsListHomeCache homeCache :
                    homeCaches) {
                try {
                    JSONObject jsonObject = new JSONObject(homeCache.getNews());
                    jsonArray.put(jsonObject);
                } catch (JSONException e) {
                    e.printStackTrace();
                    newsListCallback.onError("数据解析失败");
                    break;
                }
            }
            if (jsonArray.length() > 0) {
                newsListCallback.onSuccess(jsonArray);
                LogUtils.d(TAG, "加载到今日头条数据 = " + jsonArray.toString());
            } else {
                newsListCallback.onError("没有本地数据");
            }
        } else {
            // 其他分类
            List<NewsListOtherCache> otherCaches = DataSupport.order("id asc").where("classid = ?", classid).limit(oneCount).offset(preCount).find(NewsListOtherCache.class);
            for (NewsListOtherCache otherCache :
                    otherCaches) {
                try {
                    JSONObject jsonObject = new JSONObject(otherCache.getNews());
                    jsonArray.put(jsonObject);
                } catch (JSONException e) {
                    e.printStackTrace();
                    newsListCallback.onError("数据解析失败");
                    break;
                }
            }
            if (jsonArray.length() > 0) {
                newsListCallback.onSuccess(jsonArray);
                LogUtils.d(TAG, "加载到其他分类数据 = " + jsonArray.toString());
            } else {
                newsListCallback.onError("没有本地数据");
            }

        }

    }

    /**
     * 从网络加载资讯列表数据
     *
     * @param table     数据表
     * @param classid   分类id
     * @param pageIndex 分页页码
     */
    private void loadNewsListFromNetwork(String table, String classid, int pageIndex, final NewsListCallback newsListCallback) {

        HashMap<String, String> parameters = new HashMap<>();
        parameters.put("table", table);
        parameters.put("classid", classid);
        parameters.put("pageIndex", String.valueOf(pageIndex));
        parameters.put("pageSize", String.valueOf(20));

        NetworkUtils.shared.get(APIs.ARTICLE_LIST, parameters, new NetworkUtils.StringCallback() {
            @Override
            public void onError(Call call, Exception e, int id) {
                newsListCallback.onError("您的网络不给力哦");
            }

            @Override
            public void onResponse(String response, int id) {
                try {
                    // 如果所有接口响应格式是统一的，这些判断是可以封装在网络请求工具类里的哦
                    if (new JSONObject(response).getString("err_msg").equals("success")) {
                        JSONObject jsonObject = new JSONObject(response);
                        JSONArray jsonArray = jsonObject.getJSONArray("data");
                        newsListCallback.onSuccess(jsonArray);
                    } else {
                        String errorInfo = new JSONObject(response).getString("info");
                        newsListCallback.onError(errorInfo);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    newsListCallback.onError("数据解析异常");
                }

            }
        });

    }


}
