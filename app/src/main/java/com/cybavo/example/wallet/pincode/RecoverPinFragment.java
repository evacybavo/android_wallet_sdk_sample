package com.cybavo.example.wallet.pincode;


import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.cybavo.example.wallet.R;
import com.cybavo.example.wallet.helper.Helpers;
import com.cybavo.example.wallet.helper.ToolbarHelper;
import com.cybavo.wallet.service.api.Callback;
import com.cybavo.wallet.service.api.Error;
import com.cybavo.wallet.service.auth.Auth;
import com.cybavo.wallet.service.auth.BackupChallenge;
import com.cybavo.wallet.service.auth.results.RecoverPinCodeResult;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;

public class RecoverPinFragment extends Fragment {

    private static final String TAG = RecoverPinFragment.class.getSimpleName();

    public RecoverPinFragment() {
        // Required empty public constructor
    }

    public static RecoverPinFragment newInstance() {
        RecoverPinFragment fragment = new RecoverPinFragment();
        return fragment;
    }

    private Step mStep = Step.VERIFY_CODE;
    private SetupViewModel mSetupViewModel;
    private Button mSubmit;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_recover_pin, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ToolbarHelper.setupToolbar(view, R.id.appBar)
                .title(R.string.title_recover_pin)
                .onBack(v -> quit())
                .done();

        mSubmit = view.findViewById(R.id.submit);
        mSubmit.setOnClickListener(v -> next());
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mSetupViewModel = ViewModelProviders.of(this,
                new SetupViewModel.Factory(getActivity().getApplication())).get(SetupViewModel.class);
        showStep(Step.VERIFY_CODE);
    }

    private void showStep(Step step) {
        switch (step) {
            case VERIFY_CODE:
                if (!fragmentExists(VerifyCodeFragment.class)) {
                    showFragment(VerifyCodeFragment.newInstance());
                    mSubmit.setText(R.string.action_next);
                }
                break;
            case PIN:
                if (!fragmentExists(SetupPinFragment.class)) {
                    showFragment(SetupPinFragment.newInstance());
                    mSubmit.setText(R.string.action_next);
                }
                break;
            case BACKUP:
                if (!fragmentExists(BackupFragment.class)) {
                    showFragment(BackupFragment.newInstance());
                    mSubmit.setText(R.string.action_done);
                }
                break;
        }
        mStep = step;
    }

    public <F extends Fragment> boolean fragmentExists(Class<F> clz) {
        return getChildFragmentManager().findFragmentByTag(clz.getSimpleName()) != null;
    }

    private void showFragment(Fragment fragment) {
        final String tag = fragment.getClass().getSimpleName();

        if (getChildFragmentManager().findFragmentByTag(tag) == null) {
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.recoverRoot, fragment, fragment.getClass().getSimpleName())
                    .commit();
        }
    }

    private void next() {
        switch (mStep) {
            case VERIFY_CODE:
                if (mSetupViewModel.getVerifyCode().getValue().isEmpty()) {
                    return;
                }
                showStep(Step.PIN);
                break;
            case PIN:
                if (!Helpers.isPinCodeValid(mSetupViewModel.getPinCode().getValue())) {
                    Helpers.showToast(getContext(), getString(R.string.message_invalid_pin));
                    return;
                }
                showStep(Step.BACKUP);
                break;
            case BACKUP:
                recoverPin();
        }
    }

    private void recoverPin() {
        final String verifyCode = mSetupViewModel.getVerifyCode().getValue();

        final String pinCode = mSetupViewModel.getPinCode().getValue();

        final String question1 = mSetupViewModel.getQuestion(0).getValue(),
                question2 = mSetupViewModel.getQuestion(1).getValue(),
                question3 = mSetupViewModel.getQuestion(2).getValue();

        final String answer1 = mSetupViewModel.getAnswer(0).getValue(),
                answer2 = mSetupViewModel.getAnswer(1).getValue(),
                answer3 = mSetupViewModel.getAnswer(2).getValue();

        if (question1.isEmpty() || question2.isEmpty() || question3.isEmpty() // questions
                || answer1.isEmpty() || answer2.isEmpty() || answer3.isEmpty() // answers
                || pinCode.isEmpty() // pinCode
                || verifyCode.isEmpty()) { // verify code
            return;
        }

        mSubmit.setEnabled(false);
        Auth.getInstance().recoverPinCode(pinCode,
                BackupChallenge.make(question1, answer1),
                BackupChallenge.make(question2, answer2),
                BackupChallenge.make(question3, answer3),
                verifyCode,
                new Callback<RecoverPinCodeResult>() {
            @Override
            public void onError(Throwable error) {
                Log.w(TAG, "recoverPinCode failed", error);
                mSubmit.setEnabled(true);
                Helpers.showToast(getContext(), "recoverPinCode failed: " + error.getMessage());
            }

            @Override
            public void onResult(RecoverPinCodeResult result) {
                mSubmit.setEnabled(true);
                Helpers.showToast(getContext(), getString(R.string.message_recover_pin_success));
                quit();
            }
        });
    }

    private void quit() {
        getFragmentManager().popBackStack();
    }
}