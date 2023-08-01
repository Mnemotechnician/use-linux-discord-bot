########################################################################
# Run this script to begin generating texts.                           #
# This script is used under the hood by TextGenerator.GeneratorProcess #
########################################################################
# Accept env variables:                                                #
# CHECKPOINT - file path                                               #
########################################################################

import json
import os
import sys

import tensorflow as tf

from text_generator import TextGenerator
from text_generator_model import TextGeneratorModel
from common import *

if ("CHECKPOINT" not in os.environ):
    print("CHECKPOINT environment variable is not set")
    exit(1)
checkpoint = os.environ["CHECKPOINT"]

# Load the vocabulary
with open(os.path.join(checkpoint, "vocab.json"), "r") as file:
    vocabulary = json.load(file)

char_to_id = tf.keras.layers.StringLookup(vocabulary=vocabulary, mask_token=MASK_TOKEN, oov_token=OOV_TOKEN)
id_to_char = tf.keras.layers.StringLookup(vocabulary=char_to_id.get_vocabulary(), invert=True, mask_token=MASK_TOKEN, oov_token=OOV_TOKEN)

model = TextGeneratorModel(
    vocab_size=len(char_to_id.get_vocabulary()),
    batch_size=BATCH_SIZE,
    embedding_dim=EMBEDDING_UNITS,
    rnn_units=RNN_UNITS
)
# Load the model
model.load_weights(os.path.join(checkpoint, "ckpt"))

generator = TextGenerator(model, id_to_char, char_to_id, 1.0)

sys.stderr.write("Generating. Type starting phrases to generate inputs.")

while True:
    phrase = input()

    text, time = generator.generate_message(phrase)
    print(phrase + text)
    print(f"{time} s")
    print("")
