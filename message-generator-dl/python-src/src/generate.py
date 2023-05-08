import json
import os
import sys

import tensorflow as tf

from text_generator import TextGenerator
from text_generator_model import TextGeneratorModel

if (not 'MODEL_SAVEDIR' in os.environ):
    print("MODEL_SAVEDIR environment variable is not set")
    exit(1)
savedir = os.environ['MODEL_SAVEDIR']

# Load the model and the vocabulary
model = tf.keras.models.load_model(savedir)
with os.open(f"{savedir}/vocabulary", os.O_RDONLY) as file:
    vocabulary = json.load(file)

char_to_id = tf.keras.layers.StringLookup(vocabulary=vocabulary, mask_token=None)
id_to_char = tf.keras.layers.StringLookup(vocabulary=char_to_id.get_vocabulary(), invert=True, mask_token=None)

generator = TextGenerator(model, id_to_char, char_to_id)

sys.stderr.write("Generating. Press enter to generate a text.")

while True:
    input()

    text, time = generator.generate()
    print(text)
    print(f"{time} ms")
    print("")
