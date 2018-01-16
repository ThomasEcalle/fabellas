package groupe_9.com.fabellas;

import android.content.Intent;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.IdpResponse;
import com.firebase.ui.auth.ResultCodes;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;

import groupe_9.com.fabellas.bo.User;

public class ConnectionActivity extends AppCompatActivity {

    private static final int RC_SIGN_IN = 123;
    private Button connectPhoneBtn;
    private Button connectGuestBtn;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabaseReference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connection);
        mAuth = FirebaseAuth.getInstance();
        mDatabaseReference = FirebaseDatabase.getInstance().getReference("Users");
        if (mAuth.getCurrentUser() != null) {
            startActivity(new Intent(this, MapActivity.class));
            finish();
        }
        connectPhoneBtn = findViewById(R.id.connectPhoneBtn);
        connectGuestBtn = findViewById(R.id.connectGuestBtn);

        connectPhoneBtn.setOnClickListener(view -> {
            // Choose authentication providers
            List<AuthUI.IdpConfig> providers = Arrays.asList(
                    new AuthUI.IdpConfig.Builder(AuthUI.PHONE_VERIFICATION_PROVIDER).build());

            // Create and launch sign-in intent
            startActivityForResult(
                    AuthUI.getInstance()
                            .createSignInIntentBuilder()
                            .setAvailableProviders(providers)
                            .build(),
                    RC_SIGN_IN);
        });
        connectGuestBtn.setOnClickListener(view -> {
            mAuth.signInAnonymously()
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            startActivity(new Intent(ConnectionActivity.this, MapActivity.class));
                            finish();
                        } else {
                            Log.w("ConnectionActivity", "signInAnonymously:failure", task.getException());
                        }
                    });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mAuth.getCurrentUser() != null) {
            startActivity(new Intent(this, MapActivity.class));
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            if (resultCode == ResultCodes.OK) {
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if(true){
                    //verif de l'existence du user
                }
                mDatabaseReference.child(user.getUid()).setValue(new User(user.getUid(), user.getPhoneNumber(), null));
                startActivity(new Intent(ConnectionActivity.this, MapActivity.class));
                finish();
            } else {
                Toast.makeText(this, "failed authentification", Toast.LENGTH_LONG).show();
            }
        }
    }
}
