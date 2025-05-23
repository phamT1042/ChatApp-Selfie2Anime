package com.example.chatappfirebase.activities;

import androidx.appcompat.app.AppCompatActivity;

import android.app.ActivityOptions;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Pair;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.chatappfirebase.R;
import com.example.chatappfirebase.utilities.Constants;
import com.example.chatappfirebase.utilities.PreferenceManager;

//Màn hình giới thiệu thứ hai của ứng dụng Android, hoạt động như một màn hình chào mừng/điểm chuyển hướng.
//File kiểm tra trạng thái đăng nhập của người dùng và cung cấp các nút điều hướng đến đăng nhập, đăng ký, hoặc trợ giúp.
public class IntroActivity_2 extends AppCompatActivity {

    ImageView img_touch;
    TextView text_touch;

    //    Đối tượng dùng để quản lý các tham số của ứng dụng, như trạng thái đăng nhập.
    private PreferenceManager preferenceManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferenceManager = new PreferenceManager(getApplicationContext());

        // Kiểm tra xem người dùng đã đăng nhập chưa bằng cách đọc khóa KEY_IS_SIGNED_IN từ PreferenceManager.
        if (preferenceManager.getBoolean(Constants.KEY_IS_SIGNED_IN)) {
            // Nếu đã đăng nhập, chuyển hướng ngay đến MainActivity
            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
            startActivity(intent);
            finish();
        }
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        decorView.setSystemUiVisibility(uiOptions);

        setContentView(R.layout.activity_intro2);

        TextView btn_touch = findViewById(R.id.btn_touch);
        Button btn_login = findViewById(R.id.btn_login);
        TextView btn_signup = findViewById(R.id.btn_signup);
        TextView btn_help = findViewById(R.id.btn_help);

        img_touch = findViewById(R.id.img_touch);
        text_touch = findViewById(R.id.text_touch);

        preferenceManager = new PreferenceManager(getApplicationContext());

        btn_touch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(IntroActivity_2.this, "Touch ID not found", Toast.LENGTH_SHORT).show();
            }
        });

        btn_login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(IntroActivity_2.this, SignInActivity.class));
                finish();
            }
        });

        btn_signup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(IntroActivity_2.this, SignUpActivity.class));
                finish();
            }
        });
        btn_help.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = "https://youtube.com";
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(url));
                startActivity(intent);
            }
        });


    }
}