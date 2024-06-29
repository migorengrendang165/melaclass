package com.fpcameradetection;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

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

    Button btActCekrek;
    ImageView ivPreviewGambar;
    TextView tvPredictionResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btActCekrek = findViewById(R.id.actCekrek);
        ivPreviewGambar = findViewById(R.id.previewGambar);
        tvPredictionResult = findViewById(R.id.predictionResult);

        btActCekrek.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent it = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(it, 222);
            }
        });


    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case (222): prosesKamera(data); break;
            }
        }
    }

    // Define the response object to map the JSON response from the server
    public class UploadResponse {
        private String message;
        private String filename;
        private String predicted_class;
        private float confidence_score;

        public String getMessage() {
            return message;
        }

        public String getFilename() {
            return filename;
        }

        public String getPredictedClass() {
            return predicted_class;
        }
        public float getConfidence_score() { return confidence_score; }
    }


    // Add this interface definition for Retrofit
    public interface ImageUploadService {
        @Multipart
        @POST("/upload")
        Call<UploadResponse> uploadImage(@Part MultipartBody.Part image);
    }

    private void prosesKamera(Intent data) {
        File folderGambar = Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        DateFormat date = new SimpleDateFormat("MM-dd-yy hh-mm-ss", Locale.ENGLISH);
        CharSequence s = date.format(new Date());
        File gambar = new File(folderGambar, s.toString() + ".png");
//                Uri uriSavedGambar = Uri.fromFile(gambar); problematik exception file uri exposed

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        Bitmap bm = (Bitmap) data.getExtras().get("data");
        ivPreviewGambar.setImageBitmap(bm);

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

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://192.168.1.100:8000/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        ImageUploadService service = retrofit.create(ImageUploadService.class);

        // Create a File object representing the image file
        File imageFile = new File(folderGambar, s.toString() + ".png");

        // Create a RequestBody object from the image file
        RequestBody requestBody = RequestBody.create(MediaType.parse("image/png"), imageFile);

        // Create a MultipartBody.Part from the RequestBody
        MultipartBody.Part imagePart = MultipartBody.Part.createFormData("image", imageFile.getName(), requestBody);

        // Call the uploadImage method of the service interface
        Call<UploadResponse> call = service.uploadImage(imagePart);
        call.enqueue(new Callback<UploadResponse>() {
            @Override
            public void onResponse(Call<UploadResponse> call, Response<UploadResponse> response) {
                if (response.isSuccessful()) {
                    UploadResponse uploadResponse = response.body();
                    if (uploadResponse != null) {
                        String predictedClass = uploadResponse.getPredictedClass();
                        Double confidenceScore = uploadResponse.getConfidence_score();
                        tvPredictionResult.setText("Prediksi kanker kulit: " + predictedClass + "Confidence: " + confidenceScore);
                        Toast.makeText(MainActivity.this, "Image uploaded successfully. Prediction: " + uploadResponse.getPredictedClass(), Toast.LENGTH_LONG).show();
                    }
                    // Toast.makeText(MainActivity.this, "Image uploaded successfully", Toast.LENGTH_SHORT).show();
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
}
