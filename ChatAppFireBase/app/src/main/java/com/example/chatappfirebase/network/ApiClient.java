package com.example.chatappfirebase.network;

import retrofit2.Retrofit;
import retrofit2.converter.scalars.ScalarsConverterFactory;

// Đây là một lớp tiện ích (utility class) sử dụng thư viện Retrofit để tạo một client HTTP nhằm gửi yêu cầu đến API của
// Firebase Cloud Messaging (FCM). Lớp này được sử dụng trong ChatActivity.java để gửi thông báo đẩy khi người
// nhận không trực tuyến (isReceiverAvailable == false), đảm bảo người nhận được thông báo về tin nhắn mới
// (văn bản, ảnh, hoặc ảnh anime).
// Hiện tại thì cái này đang không được sử dụng do đã bị disabled.
public class ApiClient {

    private static Retrofit retrofit = null;

    public static Retrofit getClient() {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl("https://fcm.googleapis.com/fcm/")
                    .addConverterFactory(ScalarsConverterFactory.create())
                    .build();
        }
        return retrofit;
    }
}
