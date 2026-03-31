import re

with open('app/src/main/java/hcmute/edu/vn/tickticktodo/service/FloatingAssistantService.java', 'r', encoding='utf-8') as f:
    content = f.read()

# Fix initGenerativeModel duplication
content = content.replace("""        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        GenerativeModel gm = new GenerativeModel(
                "gemini-pro", // Có thể thay thế bằng gemini-1.5-pro/flash tuỳ library
                GEMINI_API_KEY
        );
        modelFutures = GenerativeModelFutures.from(gm);
        
        initSpeechRecognizer();


        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        GenerativeModel gm = new GenerativeModel(
                "gemini-pro", // Có thể thay thế bằng gemini-1.5-pro/flash tuỳ library
                GEMINI_API_KEY
        );
        modelFutures = GenerativeModelFutures.from(gm);
        
        initSpeechRecognizer();""", """        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        GenerativeModel gm = new GenerativeModel(
                "gemini-pro", // Có thể thay thế bằng gemini-1.5-pro/flash tuỳ library
                GEMINI_API_KEY
        );
        modelFutures = GenerativeModelFutures.from(gm);
        
        initSpeechRecognizer();""")

# Fix btnMic duplication
content = content.replace("""        btnMic.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Đang lắng nghe...", Toast.LENGTH_SHORT).show();
                    speechRecognizer.startListening(speechRecognizerIntent);
                } else {
                    Toast.makeText(this, "Chưa có quyền Micro. Hãy cấp quyền!", Toast.LENGTH_SHORT).show();
                }
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                speechRecognizer.stopListening();
                return true;
            }
            return false;
        });

        btnMic.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Đang lắng nghe...", Toast.LENGTH_SHORT).show();
                    speechRecognizer.startListening(speechRecognizerIntent);
                } else {
                    Toast.makeText(this, "Chưa có quyền Micro. Hãy cấp quyền!", Toast.LENGTH_SHORT).show();
                }
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                speechRecognizer.stopListening();
                return true;
            }
            return false;
        });""", """        btnMic.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Đang lắng nghe...", Toast.LENGTH_SHORT).show();
                    speechRecognizer.startListening(speechRecognizerIntent);
                } else {
                    Toast.makeText(this, "Chưa có quyền Micro. Hãy cấp quyền!", Toast.LENGTH_SHORT).show();
                }
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                speechRecognizer.stopListening();
                return true;
            }
            return false;
        });""")

with open('app/src/main/java/hcmute/edu/vn/tickticktodo/service/FloatingAssistantService.java', 'w', encoding='utf-8') as f:
    f.write(content)
