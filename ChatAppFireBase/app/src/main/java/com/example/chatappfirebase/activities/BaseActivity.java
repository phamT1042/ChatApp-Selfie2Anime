package com.example.chatappfirebase.activities;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.chatappfirebase.utilities.Constants;
import com.example.chatappfirebase.utilities.PreferenceManager;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

// Đây là một lớp Activity cơ sở mà các Activity khác trong ứng dụng kế thừa. File này quản lý trạng thái
// trực tuyến (availability) của người dùng bằng cách cập nhật trường KEY_AVAILABILITY trong Firestore khi Activity
// chuyển trạng thái (resume/pause). Nó đảm bảo rằng trạng thái trực tuyến của người dùng được đồng bộ với
// Firebase Firestore dựa trên việc ứng dụng đang hoạt động hay không.
public class BaseActivity extends AppCompatActivity {

    // Tham chiếu đến tài liệu của người dùng hiện tại trong collection users của Firestore.
    // Được sử dụng để cập nhật trạng thái KEY_AVAILABILITY.
    private DocumentReference documentReference;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // đối tượng để truy cập dữ liệu lưu trữ cục bộ
        PreferenceManager preferenceManager = new PreferenceManager(getApplicationContext());
        FirebaseFirestore database = FirebaseFirestore.getInstance();

        // Tạo tham chiếu đến tài liệu người dùng dựa trên KEY_USER_ID lấy từ PreferenceManager.
        documentReference = database.collection(Constants.KEY_COLLECTION_USERS)
                .document(preferenceManager.getString(Constants.KEY_USER_ID));
    }

    @Override
    // Gọi khi Activity bị tạm dừng (ví dụ: người dùng chuyển sang ứng dụng khác hoặc Activity mất focus).
    // Cập nhật trạng thái KEY_AVAILABILITY trong Firestore thành 0 (người dùng không trực tuyến).
    protected void onPause() {
        super.onPause();
        documentReference.update(Constants.KEY_AVAILABILITY, 0);
    }

    @Override
    // Cập nhật KEY_AVAILABILITY trong Firestore thành 1 (trực tuyến).
    protected void onResume() {
        super.onResume();
        documentReference.update(Constants.KEY_AVAILABILITY, 1);
    }
}
