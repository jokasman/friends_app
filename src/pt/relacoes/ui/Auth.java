package pt.relacoes.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;
import android.widget.Toast;

import pt.relacoes.core.Reminder;
import pt.relacoes.data.Prefs;

/** Confirmação com impressão digital antes de marcar um alarme como feito. */
final class Auth {
    private Auth() {}

    /** Corre "ok" se o lembrete não precisar de confirmação, ou depois de a impressão digital ser aceite. */
    static void confirmDone(Activity a, Reminder r, Runnable ok) {
        if (r == null || !r.alarm || !Prefs.requireBiometric(a)) { ok.run(); return; }
        require(a, "Confirmar que está feito", ok);
    }

    static boolean available(Activity a) {
        if (Build.VERSION.SDK_INT >= 30) {
            BiometricManager bm = a.getSystemService(BiometricManager.class);
            return bm != null && bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG
                    | BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS;
        }
        if (Build.VERSION.SDK_INT == 29) {
            BiometricManager bm = a.getSystemService(BiometricManager.class);
            return bm != null && bm.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS;
        }
        if (Build.VERSION.SDK_INT == 28) return a.getPackageManager().hasSystemFeature(PackageManager.FEATURE_FINGERPRINT);
        return false;
    }

    static void require(Activity a, String title, Runnable ok) {
        if (Build.VERSION.SDK_INT >= 28 && available(a)) {
            BiometricPrompt.Builder b = new BiometricPrompt.Builder(a)
                    .setTitle(title)
                    .setSubtitle("Use a impressão digital")
                    .setNegativeButton("Cancelar", a.getMainExecutor(), (d, w) -> { });
            if (Build.VERSION.SDK_INT >= 30) {
                b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.BIOMETRIC_WEAK);
            }
            try {
                b.build().authenticate(new CancellationSignal(), a.getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                    @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) { ok.run(); }

                    @Override public void onAuthenticationError(int code, CharSequence msg) {
                        if (code == BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS || code == BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT
                                || code == BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE) {
                            fallback(a, ok);
                        } else if (code != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED && code != BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                                && code != BiometricPrompt.BIOMETRIC_ERROR_NEGATIVE_BUTTON) {
                            Toast.makeText(a, msg, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                return;
            } catch (RuntimeException e) {
                // continua para a alternativa
            }
        }
        fallback(a, ok);
    }

    /** Telemóvel sem impressão digital configurada: pede confirmação explícita. */
    private static void fallback(Activity a, Runnable ok) {
        new AlertDialog.Builder(a).setTitle("Sem impressão digital configurada")
                .setMessage("Configure uma impressão digital nas definições do Android para proteger este passo. Marcar como feito agora?")
                .setPositiveButton("Marcar como feito", (d, w) -> ok.run())
                .setNegativeButton("Cancelar", null).show();
    }
}
