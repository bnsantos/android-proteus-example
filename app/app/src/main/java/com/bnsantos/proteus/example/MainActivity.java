package com.bnsantos.proteus.example;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.view.PagerAdapter;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;

import com.flipkart.android.proteus.EventType;
import com.flipkart.android.proteus.ImageLoaderCallback;
import com.flipkart.android.proteus.builder.DataAndViewParsingLayoutBuilder;
import com.flipkart.android.proteus.builder.LayoutBuilderCallback;
import com.flipkart.android.proteus.builder.LayoutBuilderFactory;
import com.flipkart.android.proteus.toolbox.BitmapLoader;
import com.flipkart.android.proteus.toolbox.Styles;
import com.flipkart.android.proteus.view.ProteusView;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jakewharton.retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function3;
import io.reactivex.schedulers.Schedulers;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity {
  private static final String TAG = MainActivity.class.getSimpleName();
  private ViewService mService;
  private ViewGroup mParent;

  private DataAndViewParsingLayoutBuilder mLayoutBuilder;
  private Map<String, JsonObject> mLayouts;

  /**
   * Implementation of LayoutBuilderCallback. This is where we get callbacks from proteus regarding
   * errors and events.
   */
  private LayoutBuilderCallback mCallBack = new LayoutBuilderCallback() {
    @Override
    public void onUnknownAttribute(String attribute, JsonElement value, ProteusView view) {
      Log.i("unknown-attribute", attribute + " in " + view.getViewManager().getLayout().toString());
    }

    @Nullable
    @Override
    public ProteusView onUnknownViewType(String type, View parent, JsonObject layout, JsonObject data, int index, Styles styles) {
      return null;
    }

    @Override
    public JsonObject onLayoutRequired(String type, ProteusView parent) {
      return null;
    }

    @Override
    public void onViewBuiltFromViewProvider(ProteusView view, View parent, String type, int index) {

    }

    @Override
    public View onEvent(ProteusView view, JsonElement value, EventType eventType) {
      Log.d("event", value.toString());
      return (View) view;
    }

    @Override
    public PagerAdapter onPagerAdapterRequired(ProteusView parent, List<ProteusView> children, JsonObject layout) {
      return null;
    }

    @Override
    public Adapter onAdapterRequired(ProteusView parent, List<ProteusView> children, JsonObject layout) {
      return null;
    }
  };


  /**
   * Simple implementation of BitmapLoader for loading images from url in background.
   */
  private BitmapLoader mBitmapLoader = new BitmapLoader() {
    @Override
    public Future<Bitmap> getBitmap(String imageUrl, View view) {
      return null;
    }

    @Override
    public void getBitmap(String imageUrl, final ImageLoaderCallback callback, View view, JsonObject layout) {
      URL url;

      try {
        url = new URL(imageUrl);
      } catch (MalformedURLException e) {
        e.printStackTrace();
        return;
      }

      new AsyncTask<URL, Integer, Bitmap>() {
        @Override
        protected Bitmap doInBackground(URL... params) {
          try {
            return BitmapFactory.decodeStream(params[0].openConnection().getInputStream());
          } catch (IOException e) {
            e.printStackTrace();
          }
          return null;
        }

        protected void onPostExecute(Bitmap result) {
          callback.onResponse(result);
        }
      }.execute(url);
    }
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    Retrofit retrofit = new Retrofit.Builder()
        .baseUrl(BuildConfig.ENDPOINT)
        .addConverterFactory(GsonConverterFactory.create())
        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
        .build();
    mService = retrofit.create(ViewService.class);

    mParent = (ViewGroup) findViewById(R.id.contentLayout);

    // create a new DataAndViewParsingLayoutBuilder
    // and set layouts, callback and image loader.
    mLayoutBuilder = new LayoutBuilderFactory().getDataAndViewParsingLayoutBuilder(mLayouts);
  }

  @Override
  protected void onResume() {
    super.onResume();
    fetchUI();
    if (mLayoutBuilder != null) {
      mLayoutBuilder.setListener(mCallBack);
      mLayoutBuilder.setBitmapLoader(mBitmapLoader);
    }
  }

  @Override
  protected void onStop() {
    super.onStop();
    if (mLayoutBuilder != null) {
      mLayoutBuilder.setListener(null);
      mLayoutBuilder.setBitmapLoader(null);
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.refresh_menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == R.id.refresh) {
      fetchUI();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  private void fetchUI(){
    Observable.zip(fetchLayout(), fetchData(), fetchStyles(), new Function3<JsonObject, JsonObject, Styles, Data>() {
      @Override
      public Data apply(JsonObject layout, JsonObject data, Styles style) throws Exception {
        return new Data(layout, data, style);
      }
    }).subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(new Consumer<Data>() {
        @Override
        public void accept(Data data) throws Exception {
          refreshUI(data.layout, data.data, data.styles);
        }
      });
  }

  private Observable<JsonObject> fetchLayout(){
    return fetch("main_layout.json");
  }

  private Observable<JsonObject> fetchData(){
    return fetch("main_data.json");
  }

  private Observable<JsonObject> fetch(String data){
    return mService.get(data)
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread());
  }

  private Observable<Styles> fetchStyles(){
    return mService.getStyles()
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread());
  }

  private void refreshUI(JsonObject layout, JsonObject data, Styles styles){
    mParent.removeAllViews();
    mLayoutBuilder.setLayouts(mLayouts);
    // Inflate a new view using proteus
    long start = System.currentTimeMillis();
    ProteusView view = mLayoutBuilder.build(mParent, layout, data, 0, styles);
    Log.i(TAG, "Time elapsed inflate views: " + Long.toString(System.currentTimeMillis() - start));

    mParent.addView((View) view);
  }

  private class Data{
    final JsonObject layout;
    final JsonObject data;
    final Styles styles;

    Data(JsonObject layout, JsonObject data, Styles styles) {
      this.layout = layout;
      this.data = data;
      this.styles = styles;
    }
  }

}
