package cl.fernando.nubedj;

import android.accounts.Account;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.os.SystemClock;

import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.ClearTokenRequest;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public final class AuthManager {
    public static final String SCOPE = "https://www.googleapis.com/auth/drive.readonly";
    private final Context context;
    private final SharedPreferences preferences;
    private final Object refreshLock = new Object();
    private volatile String token;
    private volatile long expiresAt;
    private volatile boolean enabled;

    public AuthManager(Context context) {
        this.context = context.getApplicationContext();
        preferences = context.getSharedPreferences("drive-account", Context.MODE_PRIVATE);
        enabled = preferences.getBoolean("enabled", false);
    }

    public boolean isConnected() { return enabled; }
    public String accountLabel() { return preferences.getString("email", "Google Drive"); }

    private AuthorizationRequest request(boolean chooseAccount) {
        AuthorizationRequest.Builder builder = AuthorizationRequest.builder()
                .setRequestedScopes(Collections.singletonList(new Scope(SCOPE)));
        String email = preferences.getString("email", "");
        if (chooseAccount) builder.setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT);
        else if (!email.isEmpty()) builder.setAccount(new Account(email, "com.google"));
        return builder.build();
    }

    public Task<AuthorizationResult> authorize(Activity activity) {
        return Identity.getAuthorizationClient(activity).authorize(request(!enabled));
    }

    public synchronized void acceptInteractive(AuthorizationResult result) throws IOException {
        verify(result);
        enabled = true;
        token = result.getAccessToken();
        expiresAt = SystemClock.elapsedRealtime() + TimeUnit.MINUTES.toMillis(50);
        preferences.edit().putBoolean("enabled", true).apply();
    }

    public synchronized void rememberAccount(String email) {
        if (email != null && !email.isEmpty() && enabled) preferences.edit().putString("email", email).apply();
    }

    private static void verify(AuthorizationResult result) throws IOException {
        if (result.hasResolution() || result.getAccessToken() == null || !result.getGrantedScopes().contains(SCOPE)) {
            throw new NeedsAuthorization();
        }
    }

    public String accessToken() throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IllegalStateException("Token requested on main thread");
        if (!enabled) throw new NeedsAuthorization();
        if (token != null && SystemClock.elapsedRealtime() < expiresAt) return token;
        synchronized (refreshLock) {
            if (token != null && SystemClock.elapsedRealtime() < expiresAt) return token;
            try {
                AuthorizationResult result = Tasks.await(Identity.getAuthorizationClient(context).authorize(request(false)), 30, TimeUnit.SECONDS);
                verify(result);
                token = result.getAccessToken();
                expiresAt = SystemClock.elapsedRealtime() + TimeUnit.MINUTES.toMillis(50);
                return token;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); throw new IOException("Conexión interrumpida", e);
            } catch (NeedsAuthorization e) { throw e; }
            catch (Exception e) { throw new IOException("Vuelve a conectar Google Drive", e); }
        }
    }

    public void invalidate(String rejectedToken) throws IOException {
        synchronized (this) { if (rejectedToken != null && rejectedToken.equals(token)) { token = null; expiresAt = 0; } }
        if (rejectedToken == null) return;
        try {
            Tasks.await(Identity.getAuthorizationClient(context).clearToken(ClearTokenRequest.builder().setToken(rejectedToken).build()), 15, TimeUnit.SECONDS);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        catch (Exception ignored) { }
    }

    public synchronized void disconnect() {
        String old = token; enabled = false; token = null; expiresAt = 0; preferences.edit().clear().apply();
        if (old != null) Identity.getAuthorizationClient(context).clearToken(ClearTokenRequest.builder().setToken(old).build());
    }

    public static final class NeedsAuthorization extends IOException {
        public NeedsAuthorization() { super("Conecta Google Drive para continuar"); }
    }
}
