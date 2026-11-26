package com.gayatripatel.hirereel

import android.app.Application
import com.gayatripatel.hirereel.data.ApplicationRepository
import com.gayatripatel.hirereel.data.Clock
import com.gayatripatel.hirereel.data.JobRepository
import com.gayatripatel.hirereel.data.SessionRepository
import com.gayatripatel.hirereel.ui.applications.ApplicationsViewModel
import com.gayatripatel.hirereel.ui.feed.FeedViewModel
import com.gayatripatel.hirereel.ui.interview.InterviewViewModel
import com.gayatripatel.hirereel.ui.jobs.JobDetailViewModel
import com.gayatripatel.hirereel.ui.jobs.JobsViewModel
import com.gayatripatel.hirereel.ui.login.LoginViewModel
import com.gayatripatel.hirereel.upload.FakeUploadApi
import com.gayatripatel.hirereel.upload.UploadApi
import com.gayatripatel.hirereel.upload.UploadScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.dsl.module

val appModule = module {
    single<Clock> { Clock { System.currentTimeMillis() } }
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { JobRepository(get()) }
    single { ApplicationRepository(get()) }
    single { SessionRepository(androidContext()) }
    single<UploadApi> { FakeUploadApi() }
    single { UploadScheduler(androidContext(), get(), get()) }

    viewModel { LoginViewModel(get()) }
    viewModel { FeedViewModel(get()) }
    viewModel { JobsViewModel(get()) }
    viewModel { params -> JobDetailViewModel(params.get(), get()) }
    viewModel { params -> InterviewViewModel(params.get(), get(), get(), get(), get(), androidContext().filesDir) }
    viewModel { ApplicationsViewModel(get(), get(), get(), get()) }
}

class HireReelApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@HireReelApp)
            modules(appModule)
        }
    }
}
