package net.jubbery.symai;

import com.theokanning.openai.audio.CreateTranscriptionRequest;
import com.theokanning.openai.audio.TranscriptionResult;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import okhttp3.*;
import net.minecraftforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

import javax.sound.sampled.*;
import java.io.*;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(SYMAI_Mod.MODID)
public class SYMAI_Mod {
    @Getter
    private final AtomicBoolean isRecording = new AtomicBoolean(false);
    // Define mod id in a common place for everything to reference
    public static final String MODID = "symai";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
    private final OpenAiService openAiService;

    private final OkHttpClient httpClient = new OkHttpClient();

    public SYMAI_Mod() {
        LOGGER.info("Starting SYMAI");
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new KeyInputHandler(this));
        // Initialize OpenAiService with your OpenAI API key
        openAiService = new OpenAiService("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"); // replace YOUR_OPENAI_API_KEY with your actual key
    }

    @Mod.EventBusSubscriber(modid = SYMAI_Mod.MODID, value = Dist.CLIENT)
    public static class KeyInputHandler {

        private final SYMAI_Mod modInstance;

        public KeyInputHandler(SYMAI_Mod mod) {
            this.modInstance = mod;
        }

        @SubscribeEvent
        public void onKeyInput(InputEvent.Key event) {

            Minecraft minecraft = Minecraft.getInstance();
            Player player = minecraft.player;

            if (player == null) {
                return;
            }

            // Check for the Villager in crosshairs
            HitResult hitResult = minecraft.hitResult;
            assert hitResult != null;
            if (hitResult.getType() == HitResult.Type.ENTITY) {
                EntityHitResult entityHitResult = (EntityHitResult) hitResult;
                Entity entity = entityHitResult.getEntity();
//                LOGGER.info(String.valueOf(event.getKey()));
//                LOGGER.info(String.valueOf(event.getAction()));
                if (entity instanceof Villager && player.distanceTo(entity) <= 5.0D) {
//                    LOGGER.info("Detected Villager");
//                    LOGGER.info("Distance is valid");

                    if (event.getKey() == GLFW.GLFW_KEY_TAB && event.getAction() == GLFW.GLFW_REPEAT) {
//                        LOGGER.info("TAB key detected");
//                        LOGGER.info("Repeat action detected");

                        boolean isCurrentlyRecording = modInstance.getIsRecording().get();
                        if (!isCurrentlyRecording) {
                            LOGGER.info("Recording TRUE");
                            modInstance.getIsRecording().set(true);
                            CompletableFuture<Void> recordingTask = CompletableFuture.runAsync(modInstance::captureAudioFromMicrophone);

                        }

                    } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                        LOGGER.info("Recording FALSE");
                        modInstance.getIsRecording().set(false);
                    }
                }
            }


        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Player player = event.player;

        // Using the getEntitiesOfClass method to fetch nearby villagers
        List<LivingEntity> nearbyVillagers = player.level().getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(5.0D),
                (entity) -> entity instanceof Villager
        );

        if (nearbyVillagers.isEmpty()) {
            return;
        }

        // Here, you can consider adding additional logic to prevent
        // the code from running every tick a villager is nearby to avoid spamming.

        // The rest of your logic:
        // Step 3: Capture Voice, Step 4: Chat with GPT, etc.
    }

    public static void startService() {

    }

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private void captureAudioFromMicrophone() {
        AudioFormat format = new AudioFormat(44100.0f, 16, 2, true, true);
        TargetDataLine microphone;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        LOGGER.info("Starting recording");
        try {
            microphone = AudioSystem.getTargetDataLine(format);
            microphone.open(format);
            microphone.start();
            while (this.getIsRecording().get()) {
                int numBytesRead = microphone.read(data, 0, data.length);
                out.write(data, 0, numBytesRead);
            }
            microphone.close();
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
        try {
            String translatedSpeech = capturePlayerVoice(data);
            LOGGER.info(translatedSpeech);
        } catch (IOException e) {
            LOGGER.error("Error while capturing or processing audio", e);
        }
    }

    private String capturePlayerVoice(byte[] audioData) throws IOException {
        // Construct the CreateTranscriptionRequest
        CreateTranscriptionRequest transcriptionRequest = CreateTranscriptionRequest.builder()
                .model("whisper-1")
                .language("en")
                .responseFormat("json")
                .prompt("You are a minecraft villager")
                .build();

        LOGGER.info("Requesting Transcript...");
        TranscriptionResult transcriptionResult = openAiService.createTranscription(transcriptionRequest, saveToWav(audioData));
        try (FileInputStream fis = new FileInputStream("somefile.txt")) {
            if (!transcriptionResult.getText().isEmpty()) {
                String transcribedText = transcriptionResult.getText();
                LOGGER.info(transcribedText);
                return transcribedText;
            }
        } catch (IOException e) {
            LOGGER.error("Error while transcribing voice using Whisper ASR", e);
        }

        return "Error capturing voice";
    }

    public static File saveToWav(byte[] audioData) throws IOException {
        // Define the audio format parameters
        AudioFormat format = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED, // Encoding
                44100.0f,                         // Sample rate (44.1KHz)
                16,                               // Bits per sample (16 bits)
                2,                                // Channels (2 for stereo)
                4,                                // Frame size
                44100.0f,                         // Frame rate
                false                             // Little endian
        );

        // Create an audio input stream from the byte array
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(audioData);
        AudioInputStream audioInputStream = new AudioInputStream(byteArrayInputStream, format, audioData.length / format.getFrameSize());

        // Use the AudioSystem to write to a temporary file
        File tempFile = File.createTempFile("recordedAudio", ".wav");
        AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, tempFile);

        return tempFile;
    }


    private String chatWithGPT(String input) {
        // Use the Chat GPT API to get a response for the given input
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.getMessages().add(new ChatMessage("user", input));
        ChatCompletionResult result = openAiService.createChatCompletion(request);

        return result.getObject(); // Get the model's response;
    }

    private byte[] convertTextToSpeech(String text) {
        // Use a Text-to-Speech library to convert the text to audio data
        // Return the audio data
        return new byte[0];
    }

    private void playAudioToPlayer(Player player, byte[] audioData) {
        // Play the audio data to the specified player
        // This would likely involve creating a custom sound event or using another method
    }
}