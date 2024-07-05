package com.example.melaclass;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import android.content.SharedPreferences;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_CAPTURE = 222;
    private static final int REQUEST_IMAGE_GALLERY = 333;

    Button btActCekrek, btChooseFromGallery, btViewHistory;
    ImageView ivPreviewGambar;
    TextView tvPredictionResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btActCekrek = findViewById(R.id.actCekrek);
        btChooseFromGallery = findViewById(R.id.chooseFromGallery);
        btViewHistory = findViewById(R.id.viewHistory);
        ivPreviewGambar = findViewById(R.id.previewGambar);
        tvPredictionResult = findViewById(R.id.predictionResult);

        btActCekrek.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent it = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(it, REQUEST_IMAGE_CAPTURE);
            }
        });

        btChooseFromGallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(intent, REQUEST_IMAGE_GALLERY);
            }
        });

        btViewHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case REQUEST_IMAGE_CAPTURE:
                    handleCameraImage(data);
                    break;
                case REQUEST_IMAGE_GALLERY:
                    handleGalleryImage(data);
                    break;
            }
        }
    }

    private void handleCameraImage(Intent data) {
        Bitmap bm = (Bitmap) data.getExtras().get("data");
        saveAndUploadImage(bm);
    }

    private void handleGalleryImage(Intent data) {
        Uri selectedImage = data.getData();
        try {
            Bitmap bm = MediaStore.Images.Media.getBitmap(this.getContentResolver(), selectedImage);
            saveAndUploadImage(bm);
        } catch (IOException e) {
            Log.e("Main", "Error loading image from gallery: " + e.getMessage());
        }
    }

    private void saveAndUploadImage(Bitmap bm) {
        ivPreviewGambar.setImageBitmap(bm);

        File folderGambar = Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        DateFormat date = new SimpleDateFormat("MM-dd-yy hh-mm-ss", Locale.ENGLISH);
        CharSequence s = date.format(new Date());
        File gambar = new File(folderGambar, s.toString() + ".png");

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.PNG, 100, stream);
        byte[] byteArray = stream.toByteArray();

        try {
            FileOutputStream fo = new FileOutputStream(gambar);
            fo.write(byteArray);
            fo.flush();
            fo.close();
        } catch (FileNotFoundException e) {
            Log.e("Main", "File not found: " + e.getMessage());
        } catch (IOException e) {
            Log.e("Main", "IOException: " + e.getMessage());
        }

        Toast.makeText(this, "Gambar tersimpan: DCIM/" + s.toString() + ".png", Toast.LENGTH_SHORT).show();

        uploadImageToServer(gambar);
    }

    private void uploadImageToServer(File imageFile) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://192.168.1.100:8000/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        ImageUploadService service = retrofit.create(ImageUploadService.class);

        RequestBody requestBody = RequestBody.create(MediaType.parse("image/png"), imageFile);
        MultipartBody.Part imagePart = MultipartBody.Part.createFormData("image", imageFile.getName(), requestBody);

        Call<UploadResponse> call = service.uploadImage(imagePart);
        call.enqueue(new Callback<UploadResponse>() {
            @Override
            public void onResponse(Call<UploadResponse> call, Response<UploadResponse> response) {
                if (response.isSuccessful()) {
                    UploadResponse uploadResponse = response.body();
                    if (uploadResponse != null) {
                        String predictedClass = uploadResponse.getPredictedClass();
                        Map<String, Double> confidenceScores = uploadResponse.getPredictedProbabilities();

                        StringBuilder resultText = new StringBuilder();
                        resultText.append(predictedClass).append("\n");
                        resultText.append("Confidence Scores:\n");
                        for (Map.Entry<String, Double> entry : confidenceScores.entrySet()) {
                            resultText.append(entry.getKey())
                                    .append(": ")
                                    .append(String.format("%.2f", entry.getValue()))
                                    .append("%\n");
                        }

                        tvPredictionResult.setText(resultText.toString());
                        savePredictionToHistory(imageFile.getAbsolutePath(), resultText.toString());

                        Toast.makeText(MainActivity.this, "Image uploaded successfully. Prediction: " + predictedClass, Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Failed to upload image", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<UploadResponse> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Failed to upload image: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void savePredictionToHistory(String imagePath, String prediction) {
        // Save the image path and prediction result to shared preferences or a database
        // For simplicity, here we'll use SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences("PredictionHistory", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        int count = sharedPreferences.getInt("count", 0);
        editor.putString("imagePath_" + count, imagePath);
        editor.putString("prediction_" + count, prediction);
        editor.putInt("count", count + 1);
        editor.apply();
    }

    public interface ImageUploadService {
        @Multipart
        @POST("/upload")
        Call<UploadResponse> uploadImage(@Part MultipartBody.Part image);
    }

    public class UploadResponse {
        private String message;
        private String filename;
        private String predicted_class;
        private Map<String, Double> predicted_probabilities;

        public String getMessage() {
            return message;
        }

        public String getFilename() {
            return filename;
        }

        public String getPredictedClass() {
            return predicted_class;
        }

        public Map<String, Double> getPredictedProbabilities() {
            return predicted_probabilities;
        }
    }
}
