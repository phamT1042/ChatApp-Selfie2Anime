package com.example.chatappfirebase.cloudinary;

import android.app.Application;
import android.content.res.AssetManager;
import android.util.Log;

import com.cloudinary.android.MediaManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

//public class Config extends Application {
//    @Override
//    public void onCreate() {
//        super.onCreate();
//        MediaManager.init(this, new HashMap<String, String>() {{
//            put("cloud_name", "daqprv02j");
//            put("secure", "true");
//        }});
//    }
//}

public class Config extends Application {
    private static Properties cloudinaryProperties;

    @Override
    public void onCreate() {
        super.onCreate();
        Map<String, String> config = loadCloudinaryConfig();
        MediaManager.init(this, new HashMap<>(config));
    }

    private Map<String, String> loadCloudinaryConfig() {
        Map<String, String> config = new HashMap<>();
        cloudinaryProperties = new Properties();
        AssetManager assetManager = getAssets();

        try {
            InputStream inputStream = assetManager.open("cloudinary.properties");
            cloudinaryProperties.load(inputStream);

            config.put("cloud_name", cloudinaryProperties.getProperty("cloud_name"));
            config.put("secure", cloudinaryProperties.getProperty("secure", "true"));

            inputStream.close();
        } catch (IOException e) {
            Log.e("CloudinaryConfig", "Failed to load cloudinary.properties", e);
        }

        return config;
    }

    public static String getUploadPreset() {
        if (cloudinaryProperties != null) {
            return cloudinaryProperties.getProperty("upload_preset");
        }
        return null;
    }
}