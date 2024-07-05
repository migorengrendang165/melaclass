package com.example.melaclass;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ListView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;

public class HistoryActivity extends AppCompatActivity {

    ListView lvHistory;
    HistoryAdapter adapter;
    ArrayList<HistoryItem> historyList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        lvHistory = findViewById(R.id.lvHistory);
        historyList = new ArrayList<>();

        SharedPreferences sharedPreferences = getSharedPreferences("PredictionHistory", MODE_PRIVATE);
        int count = sharedPreferences.getInt("count", 0);

        for (int i = 0; i < count; i++) {
            String imagePath = sharedPreferences.getString("imagePath_" + i, null);
            String prediction = sharedPreferences.getString("prediction_" + i, null);
            if (imagePath != null && prediction != null) {
                historyList.add(new HistoryItem(imagePath, prediction));
            }
        }

        adapter = new HistoryAdapter(this, historyList);
        lvHistory.setAdapter(adapter);
    }
}
