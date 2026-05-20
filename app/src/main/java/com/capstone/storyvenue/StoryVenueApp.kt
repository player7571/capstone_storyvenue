package com.capstone.storyvenue

import android.app.Application
import com.kakao.sdk.common.KakaoSdk

class StoryVenueApp : Application() {
    override fun onCreate() {
        super.onCreate()
        KakaoSdk.init(this, "18a269385527bd3215270868fc9325b3")
    }
}
