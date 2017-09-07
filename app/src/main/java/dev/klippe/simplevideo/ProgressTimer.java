package dev.klippe.simplevideo;

import android.os.CountDownTimer;

/**
 * Created by user on 05.09.2017.
 */

public class ProgressTimer extends CountDownTimer {

    private OnTimer onTimer;

    public ProgressTimer(long millisInFuture, long countDownInterval) {
        super(millisInFuture, countDownInterval);
    }

    @Override
    public void onTick(long l) {
        int progress = (int) (l / 100);
        onTimer.changeProgress(progress);
    }

    public void setListener(OnTimer ot){
        onTimer = ot;
    }

    @Override
    public void onFinish() {
        onTimer.setProgressMax();
    }

    interface OnTimer{
        void changeProgress(int l);
        void setProgressMax();
    }
}
