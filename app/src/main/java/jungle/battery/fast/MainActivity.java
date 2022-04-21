package jungle.battery.fast;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

import jungle.battery.fast.utils.AESHelper;


/**
 * @author Admin
 * @version 1.0
 * @date 2017/6/24
 */

public class MainActivity extends Activity{
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View view=new View(this);
        setContentView(view);
        AESHelper.loadLibrary();
    }
}
