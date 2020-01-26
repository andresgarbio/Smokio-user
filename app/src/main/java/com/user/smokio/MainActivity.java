package com.user.smokio;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.Strategy;
import com.google.location.nearby.apps.rockpaperscissors.R;


import static java.nio.charset.StandardCharsets.UTF_8;

import android.bluetooth.BluetoothSocket;
import android.os.*;


/** Activity controlling the Rock Paper Scissors game */
public class MainActivity extends ConnectionsActivity {
  private static final Strategy STRATEGY = Strategy.P2P_CLUSTER;
  private String mName = CodenameGenerator.generate();
  private static final String SERVICE_ID =
          "com.google.location.nearby.apps.walkietalkie.manual.SERVICE_ID";

  BluetoothSocket socket;
  Handler bt_handler;
  int handlerState;



  private static final String TAG = "RockPaperScissors";

  private static final String[] REQUIRED_PERMISSIONS =
      new String[] {
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.ACCESS_COARSE_LOCATION,
      };

  private static final int REQUEST_CODE_REQUIRED_PERMISSIONS = 1;

  private enum GameChoice {
    ROCK,
    PAPER,
    SCISSORS;

    boolean beats(GameChoice other) {
      return (this == GameChoice.ROCK && other == GameChoice.SCISSORS)
          || (this == GameChoice.SCISSORS && other == GameChoice.PAPER)
          || (this == GameChoice.PAPER && other == GameChoice.ROCK);
    }
  }

  // Our handle to Nearby Connections
  private ConnectionsClient connectionsClient;

  // Our randomly generated name
  private final String codeName = CodenameGenerator.generate();

  private String opponentEndpointId;
  private String opponentName;
  private int opponentScore;
  private GameChoice opponentChoice;

  private int myScore;
  private GameChoice myChoice;

  private Button findOpponentButton;
  private Button disconnectButton;
  private Button rockButton;
  private Button paperButton;
  private Button scissorsButton;

  private TextView opponentText;
  private TextView statusText;
  private TextView scoreText;


  // Callbacks for receiving payloads

  @Override
  protected void onCreate(@Nullable Bundle bundle) {
    super.onCreate(bundle);
    setContentView(R.layout.activity_main);

    findOpponentButton = findViewById(R.id.find_opponent);
    disconnectButton = findViewById(R.id.disconnect);
    rockButton = findViewById(R.id.rock);
    paperButton = findViewById(R.id.paper);
    scissorsButton = findViewById(R.id.scissors);

    rockButton.setEnabled(true);
    paperButton.setEnabled(true);
    scissorsButton.setEnabled(true);

    opponentText = findViewById(R.id.opponent_name);
    statusText = findViewById(R.id.status);
    scoreText = findViewById(R.id.score);

    TextView nameView = findViewById(R.id.name);
    nameView.setText(getString(R.string.codename, codeName));

    connectionsClient = Nearby.getConnectionsClient(this);

    bt_handler = new Handler(){
      @Override
      public void handleMessage(Message msg) {
        if (msg.what==handlerState){
          String readMessage=(String)msg.obj;
          Log.d(TAG, readMessage);
          findOpponentButton.setVisibility(readMessage.equals("fire") ? View.VISIBLE : View.GONE );
          if(readMessage.equals("fire")){
            Log.d(TAG, "got fire");
            findOpponentButton.setEnabled(true);
          }
          rockButton.setEnabled(true);
          paperButton.setEnabled(true);
          scissorsButton.setEnabled(true);

        }
      }
    };

    resetGame();
  }

  @Override
  protected void onStart() {
    super.onStart();
    if (!hasPermissions(this, REQUIRED_PERMISSIONS)) {
      requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_REQUIRED_PERMISSIONS);
    }

  }

  @Override
  protected void onStop() {
    connectionsClient.stopAllEndpoints();
    resetGame();

    super.onStop();
  }


  /** Handles user acceptance (or denial) of our permission request. */
  @CallSuper
  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);

    if (requestCode != REQUEST_CODE_REQUIRED_PERMISSIONS) {
      return;
    }

    for (int grantResult : grantResults) {
      if (grantResult == PackageManager.PERMISSION_DENIED) {
        Toast.makeText(this, R.string.error_missing_permissions, Toast.LENGTH_LONG).show();
        finish();
        return;
      }
    }
    recreate();
  }

  /** Finds an opponent to play the game with using Nearby Connections. */
  public void findOpponent(View view) {
    //startAdvertising();
    startDiscovering();
    Log.i(TAG, "StartingDiscovery");
    setStatusText(getString(R.string.status_searching));


  }

  /** Disconnects from the opponent and reset the UI. */
  public void disconnect(View view) {
    connectionsClient.disconnectFromEndpoint(opponentEndpointId);
    resetGame();
  }

  /** Sends a {@link GameChoice} to the other player. */
  public void makeMove(View view) {
    if (view.getId() == R.id.rock) {
      sendGameChoice(GameChoice.ROCK);
    } else if (view.getId() == R.id.paper) {
      sendGameChoice(GameChoice.PAPER);
    } else if (view.getId() == R.id.scissors) {
      sendGameChoice(GameChoice.SCISSORS);
    }
  }


  /** Wipes all game state and updates the UI accordingly. */
  private void resetGame() {
    opponentEndpointId = null;
    opponentName = null;
    opponentChoice = null;
    opponentScore = 0;
    myChoice = null;
    myScore = 0;

    setOpponentName(getString(R.string.no_opponent));
    setStatusText(getString(R.string.status_disconnected));
    updateScore(myScore, opponentScore);
    setButtonState(false);
  }

  /** Sends the user's selection of rock, paper, or scissors to the opponent. */
  private void sendGameChoice(GameChoice choice) {
    myChoice = choice;
    connectionsClient.sendPayload(
        opponentEndpointId, Payload.fromBytes(choice.name().getBytes(UTF_8)));
    Log.i(TAG, "sent->" + choice);

    setStatusText(getString(R.string.game_choice, choice.name()));
    // No changing your mind!
    setGameChoicesEnabled(false);
  }

  /** Determines the winner and update game state/UI after both players have chosen. */
  private void finishRound() {
    if (myChoice.beats(opponentChoice)) {
      // Win!
      setStatusText(getString(R.string.win_message, myChoice.name(), opponentChoice.name()));
      myScore++;
    } else if (myChoice == opponentChoice) {
      // Tie, same choice by both players
      setStatusText(getString(R.string.tie_message, myChoice.name()));
    } else {
      // Loss
      setStatusText(getString(R.string.loss_message, myChoice.name(), opponentChoice.name()));
      opponentScore++;
    }

    myChoice = null;
    opponentChoice = null;

    updateScore(myScore, opponentScore);

    // Ready for another round
    setGameChoicesEnabled(true);
  }

  /** Enables/disables buttons depending on the connection status. */
  private void setButtonState(boolean connected) {
    findOpponentButton.setEnabled(true);
    findOpponentButton.setVisibility(connected ? View.GONE : View.VISIBLE);
    disconnectButton.setVisibility(connected ? View.VISIBLE : View.GONE);

    setGameChoicesEnabled(connected);
  }

  /** Enables/disables the rock, paper, and scissors buttons. */
  private void setGameChoicesEnabled(boolean enabled) {
    //rockButton.setEnabled(enabled);
    //paperButton.setEnabled(enabled);
    //scissorsButton.setEnabled(enabled);
  }

  /** Shows a status message to the user. */
  private void setStatusText(String text) {
    statusText.setText(text);
  }

  /** Updates the opponent name on the UI. */
  private void setOpponentName(String opponentName) {
    opponentText.setText(getString(R.string.opponent_name, opponentName));
  }

  /** Updates the running score ticker. */
  private void updateScore(int myScore, int opponentScore) {
    scoreText.setText(getString(R.string.game_score, myScore, opponentScore));
  }

  @Override
  protected void onEndpointConnected(Endpoint endpoint) {
    findOpponentButton.setEnabled(false);
  }

  @Override
  protected String getName() {
    return mName;
  }

  @Override
  public String getServiceId() {
    return SERVICE_ID;
  }

  /** {@see ConnectionsActivity#getStrategy()} */
  @Override
  public Strategy getStrategy() {
    return STRATEGY;
  }

  protected void onReceive(Endpoint endpoint, Payload payload) {
    disconnectButton.setVisibility(View.VISIBLE);
  }



}


