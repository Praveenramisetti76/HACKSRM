from elevenlabs import ElevenLabs

client = ElevenLabs(
    api_key="d1ca0234899fcbad23523e9c37c1eac95bc424b4f72c900e412932eef77c235e"
)

audio_stream = client.text_to_speech.convert(
    voice_id="JBFqnCBsd6RMkjVDRZzb",
    model_id="eleven_multilingual_v2",
    output_format="mp3_44100_128",
    text="The first move is what sets everything in motion."
)

with open("output.mp3", "wb") as f:
    for chunk in audio_stream:
        if chunk:
            f.write(chunk)

print("Audio saved as output.mp3")
