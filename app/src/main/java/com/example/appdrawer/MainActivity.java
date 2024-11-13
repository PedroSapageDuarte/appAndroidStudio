package com.example.appdrawer;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowInsets;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.github.anastr.speedviewlib.ImageSpeedometer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SensorEventListener, SurfaceHolder.Callback, LocationListener {

    private SensorManager sensorManager;
    private LocationManager locationManager;
    private Sensor accelerometer, gyroscope, ahrs;

    private TextView tvAccelX, tvAccelY, tvAccelZ, tvGyroX, tvGyroY, tvGyroZ, tvAHRSpitch, tvAHRSroll, tvAHRSyaw, tvGPSlat, tvGPSlong, tvSpeed;

    private SurfaceView surfaceView;
    private Camera camera;
    private SurfaceHolder surfaceHolder;
    private boolean isCollectingData = false;

    private MediaRecorder mediaRecorder;
    private boolean isRecording = false;
    private File csvFile;
    private FileWriter csvWriter;
    private ImageSpeedometer imageSpeedometer;

    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");

    DrawerLayout drawerLayout;
    ImageButton buttonDrawerToggle;
    ImageButton buttonDrawerClose;

    //Permissões
    private static final int PERMISSION_REQUEST_CODE = 100;


    private static final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        //Verificar permissões
        if (!arePermissionsGranted()) {
            requestPermissions();
        }


        //Botões para abrir o "menu"
        drawerLayout = findViewById(R.id.drawerLayout);
        buttonDrawerToggle = findViewById(R.id.buttonDrawerToggle);
        buttonDrawerClose = findViewById(R.id.buttonDrawerClose);

        //Dados do acelerómetro
        tvAccelX = findViewById(R.id.tvAccelX);
        tvAccelY = findViewById(R.id.tvAccelY);
        tvAccelZ = findViewById(R.id.tvAccelZ);

        //Dados do giroscópio
        tvGyroX = findViewById(R.id.tvGyroX);
        tvGyroY = findViewById(R.id.tvGyroY);
        tvGyroZ = findViewById(R.id.tvGyroZ);

        //Dados do AHRS
        tvAHRSpitch = findViewById(R.id.tvAHRSpitch);
        tvAHRSroll = findViewById(R.id.tvAHRSroll);
        tvAHRSyaw = findViewById(R.id.tvAHRSyaw);

        //Dados do GPS
        tvGPSlat = findViewById(R.id.tvGPSlat);
        tvGPSlong = findViewById(R.id.tvGPSlong);
        tvSpeed = findViewById(R.id.tvSpeed);

        //Velocimetro
        imageSpeedometer = findViewById(R.id.speedometer);
        imageSpeedometer.setMaxSpeed(220);
        imageSpeedometer.speedTo(0);

        //Camara
        surfaceView = findViewById(R.id.surfaceView);

        //Fullscreen
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);

        //Botões "Gaveta"
        buttonDrawerToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawerLayout.open();
            }
        });

        buttonDrawerClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawerLayout.close();
            }
        });


        //Chama a função que ajusta o layout para utilizar os insets da barra de navegação
        adjustLayoutForNavigationBar();

        //Ocultar UI do sistema no modo imersivo
        hideSystemUI();

        //Código para fazer a ligação com os sensores
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            ahrs = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    }

    //---------------------Código relativo às permissões-----------------------------
    private boolean arePermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            if (!allPermissionsGranted) {
                Toast.makeText(this, "Permissões não concedidas. A aplicação não pode funcionar sem permissões.", Toast.LENGTH_SHORT).show();
            }
            restartApp();  //Restart
            surfaceCreated(surfaceHolder);
        }
    }


    //------------------------Código para fazer a aplicação ficar em fullscreen------------------------------
    private void adjustLayoutForNavigationBar() {
        //Adiciona o listener para os insets do sistema
        ViewCompat.setOnApplyWindowInsetsListener(drawerLayout, (view, windowInsets) -> {
            WindowInsetsCompat insets = WindowInsetsCompat.toWindowInsetsCompat(windowInsets.toWindowInsets(), view);

            //Obter os insets da barra de navegação
            Insets systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            //Ajustar o padding para que o conteúdo do layout preencha o espaço da navigation bar
            view.setPadding(0, 0, 0, systemInsets.bottom);

            return insets;
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    private void hideSystemUI() {
        WindowInsetsControllerCompat windowInsetsController = ViewCompat.getWindowInsetsController(getWindow().getDecorView());
        if (windowInsetsController != null) {
            //Esconder a barra de navegação e a barra de status
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());

            //Modo imersivo sticky para manter a interface oculta ao interagir
            windowInsetsController.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
        }
    }
    //----------------------------------------------------------------------------------------------------------

    //Gravar vídeo e dados no ficheiro
    public void startRecording(View view) {
        if (!isRecording && arePermissionsGranted()) {
            isCollectingData = true;
            try {
                mediaRecorder = new MediaRecorder();
                camera.unlock();
                mediaRecorder.setCamera(camera);

                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
                mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

                mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));

                File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "MyCameraApp");
                if (!mediaStorageDir.exists()) {
                    if (!mediaStorageDir.mkdirs()) {
                        Toast.makeText(this, "Falha ao criar diretório de armazenamento", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                String videoFileName = "sensor_data" + timeStamp + ".mp4";
                File mediaFile = new File(mediaStorageDir.getPath() + File.separator + videoFileName);

                mediaRecorder.setOutputFile(mediaFile.getAbsolutePath());

                mediaRecorder.prepare();
                mediaRecorder.start();
                isRecording = true;

                //Inicia a gravação dos dados do acelerómetro no arquivo CSV
                File directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                String filename = "sensor_data_" + dateFormat.format(new Date()) + ".csv";
                csvFile = new File(directory + File.separator + filename);
                csvWriter = new FileWriter(csvFile);
                csvWriter.append("Sensor, Timestamp, Valor_X, Valor_Y, Valor_Z\n");


                Toast.makeText(this, "Gravação iniciada", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Erro ao iniciar a gravação", Toast.LENGTH_SHORT).show();
            }
        }
    }


    //Parar a gravação de vídeo e a recolha de dados
    public void stopRecording(View view) {
        if (isRecording) {
            isCollectingData = false;
            //Para a gravação de vídeo
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
                camera.lock();
                isRecording = false;

                //Para a gravação dos dados e fecha o arquivo CSV
                //sensorManager.unregisterListener(this);
                csvWriter.flush();
                csvWriter.close();

                Toast.makeText(this, "Gravação encerrada", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Erro ao encerrar a gravação", Toast.LENGTH_SHORT).show();
            }
        }
    }

    //-------------------------Código para a suportar a preview da câmara------------------------------
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        camera = Camera.open();

        Camera.Parameters params = camera.getParameters();
        if(params.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }
        if(params.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        }
        camera.setParameters(params);

        try {
            setCameraDisplayOrientation();

            camera.setPreviewDisplay(holder);
            camera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //Código para registar os sensores a serem "ouvidos"
        registerSensors();
    }

    //Ajustar orientação da câmara
    private void setCameraDisplayOrientation() {
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, cameraInfo);
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (cameraInfo.orientation + degrees) % 360;
            result = (360 - result) % 360; //Compensar a orientação espelhada da câmera frontal
        } else { //Camara traseira
            result = (cameraInfo.orientation - degrees + 360) % 360;
        }

        camera.setDisplayOrientation(result);
    }
    //---------------------------------------------------------------------------------

    //--------------------------Código relativo à atualização dos dados no ecrã e escrita em CSV---------------------------
    @Override
    public void onSensorChanged(SensorEvent event) {
        //Código relativo à atualização dos dados no ecrã em tempo real
        //Código relativo aos dados fornecidos pelo acelerómetro
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            DecimalFormat df = new DecimalFormat("#.###");

            String xFormatado = "X: " + df.format(x);
            String yFormatado = "Y: " + df.format(y);
            String zFormatado = "Z: " + df.format(z);

            tvAccelX.setText(xFormatado);
            tvAccelY.setText(yFormatado);
            tvAccelZ.setText(zFormatado);
        }
        //Código relativo aos dados fornecidos pelo giroscópio
        else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            DecimalFormat df = new DecimalFormat("#.###");

            String xFormatado = "X: " + df.format(x);
            String yFormatado = "Y: " + df.format(y);
            String zFormatado = "Z: " + df.format(z);

            tvGyroX.setText(xFormatado);
            tvGyroY.setText(yFormatado);
            tvGyroZ.setText(zFormatado);

        }
        //Código relativo aos dados fornecidos pelo AHRS
        else if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            float[] rotationMatrix = new float[9];
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);

            //Remap os ângulos de Euler para graus
            float[] orientation = new float[3];
            SensorManager.getOrientation(rotationMatrix, orientation);

            //Converter para graus
            float pitch = (float) Math.toDegrees(orientation[1]);
            float roll = (float) Math.toDegrees(orientation[2]);
            float yaw = (float) Math.toDegrees(orientation[0]);

            DecimalFormat df = new DecimalFormat("#.###");

            String pitchFormatado = "Pitch: " + df.format(pitch);
            String rollFormatado = "Roll: " + df.format(roll);
            String yawFormatado = "Yaw: " + df.format(yaw);

            tvAHRSpitch.setText(pitchFormatado);
            tvAHRSroll.setText(rollFormatado);
            tvAHRSyaw.setText(yawFormatado);
        }


        if (isCollectingData) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            long time = System.currentTimeMillis();  //Para mostrar o tempo em milissegundos

            //Escrever os dados no ficheiro CSV
            try {
                //Dados do acelerómetro
                if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                    String line = String.format(Locale.getDefault(), "ACEL," + time + "," + x + "," + y + "," + z + "\n");
                    csvWriter.append(line);
                }
                //Dados do giroscópio
                else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                    String line = String.format(Locale.getDefault(), "GYRO," + time + "," + x + "," + y + "," + z + "\n");
                    csvWriter.append(line);
                }
                //Dados do AHRS
                else if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                    //Calcular os ângulos de Euler a partir dos dados do sensor AHRS
                    float[] rotationMatrix = new float[9];
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);

                    //Remap os ângulos de Euler para graus
                    float[] orientation = new float[3];
                    SensorManager.getOrientation(rotationMatrix, orientation);

                    //Converter para graus
                    float pitch = (float) Math.toDegrees(orientation[1]);
                    float roll = (float) Math.toDegrees(orientation[2]);
                    float yaw = (float) Math.toDegrees(orientation[0]);

                    String line = String.format(Locale.getDefault(), "AHRS," + time + "," + pitch + "," + roll + "," + yaw + "\n");
                    csvWriter.append(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //Código relativo aos dados fornecidos pelo GPS
    @Override
    public void onLocationChanged(Location location) {

        float latitude = (float) location.getLatitude();
        float longitude = (float) location.getLongitude();

        float speedMeterPerSecond = location.getSpeed();
        float speedKilometerPerHour = speedMeterPerSecond * 3.6f;

        if(isCollectingData) {
            long time = System.currentTimeMillis();  //Para mostrar o tempo em milissegundos

            //Escrever no ficheiro
            String line = String.format(Locale.getDefault(), "GPS," + time + "," + latitude + "," + longitude + "," + speedKilometerPerHour + "\n");
            try {
                csvWriter.append(line);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        //Escrever os dados em tempo real na aplicação
        DecimalFormat df = new DecimalFormat("#.###");

        String latitudeFormatado = "Latitude: " + df.format(latitude);
        String longitudeFormatado = "Longitude: " + df.format(longitude);

        DecimalFormat speedForm = new DecimalFormat("#");
        String speedFormatado = speedForm.format(speedKilometerPerHour);

        tvGPSlat.setText(latitudeFormatado);
        tvGPSlong.setText(longitudeFormatado);
        tvSpeed.setText(speedFormatado);
        imageSpeedometer.speedTo(speedKilometerPerHour);
    }

    //---------------------------Código para fechar a aplicação-------------------
    @Override
    protected void onPause() {
        super.onPause();
        if (camera != null) {
            camera.stopPreview();
            camera.release();
            camera = null;
        }
        sensorManager.unregisterListener(this);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        camera.stopPreview();
        camera.release();
    }

    //------------------------------------------------------------------------
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // Não é necessário implementar
    }
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Não é necessário implementar
    }
    //------------------------------------------------------------------------------------------------------------------------

    public void restartApp() {
        Intent intent = getBaseContext().getPackageManager()
                .getLaunchIntentForPackage(getBaseContext().getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }


    public void registerSensors() {
        // Registra os sensores
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(this, ahrs, SensorManager.SENSOR_DELAY_FASTEST);

        // Verificar se as permissões de localização estão concedidas antes de solicitar atualizações de localização
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            //Solicitar atualizações de localização
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        } else {
            //Se as permissões não forem concedidas, solicita-as
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, PERMISSION_REQUEST_CODE);
        }
    }

}