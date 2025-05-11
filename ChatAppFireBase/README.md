## Cài đặt file cấu hình sau khi pull project

### 1. `google-services.json`

- **Vị trí:** `app/google-services.json`
- **Cách tạo:**
    1. Truy cập [Firebase Console](https://console.firebase.google.com/).
    2. Tạo hoặc chọn một project.
    3. Thêm Android app vào project (dùng package name của app).
    4. Tải xuống file `google-services.json`.
    5. Đặt file vào thư mục `app/`.

---

### 2. `cloudinary.properties`

- **Vị trí:** `app/src/main/assets/cloudinary.properties`
- **Nội dung mẫu:**
  ```properties
  cloud_name="your_cloud_name"
  upload_preset="your_preset_name"
  secure=true