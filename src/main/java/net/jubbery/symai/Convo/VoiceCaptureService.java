package net.jubbery.symai.Convo;

import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.service.OpenAiService;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VoiceCaptureService {

    private static final Logger LOGGER = LoggerFactory.getLogger(VoiceCaptureService.class);
    private final OpenAiService openAiService;

    public VoiceCaptureService(String apiKey) {
        this.openAiService = new OpenAiService(apiKey);
    }

    public String capturePlayerVoice(byte[] audioData) {
        try {
//            ChatCompletionResult result = openAiService.chatWithModel(request);
            ChatCompletionResult result = new ChatCompletionResult(); // FIX
            String transcribedText = result.getChoices().get(0).getMessage().getContent();
            LOGGER.info(transcribedText);
            return transcribedText;
        } catch (Exception e) {
            LOGGER.error("Error while transcribing voice using Whisper ASR", e);
        }

        return "Error capturing voice";
    }
}