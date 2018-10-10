package henry.newgeohashing;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.widget.ImageView;


public class SplashActivity extends AppCompatActivity{
    private static int SPLASH_TIMEOUT = 4000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        Handler handler = new Handler();
        handler.postDelayed(new Runnable(){
            @Override
            public void run() {
                Intent mapIntent = new Intent(SplashActivity.this, MapActivity.class);
                startActivity(mapIntent);
                finish();
            }
        }, SPLASH_TIMEOUT);
    }


}
