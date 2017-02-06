package com.bnsantos.proteus.example;

import com.flipkart.android.proteus.toolbox.Styles;
import com.google.gson.JsonObject;

import io.reactivex.Observable;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface ViewService {
  @GET("{layout}")
  Observable<JsonObject> get(@Path("layout") String layout);


  @GET("styles.json")
  Observable<Styles> getStyles();
}
