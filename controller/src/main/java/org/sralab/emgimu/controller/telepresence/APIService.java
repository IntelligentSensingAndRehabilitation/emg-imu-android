package org.sralab.emgimu.controller.telepresence;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface APIService {

    @POST("/api/teleprescence/")
    Call<Status> control(@Body Post post);
}