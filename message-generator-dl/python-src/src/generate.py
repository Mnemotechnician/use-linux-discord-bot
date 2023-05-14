########################################################################
# Run this script to begin generating texts.                           #
# This script is used under the hood by TextGenerator.GeneratorProcess #
########################################################################
# Accept env variables:                                                #
# MODEL_SAVEFILE, VOCAB_SAVEFILE - file pathes                         #
########################################################################

import json
import os
import sys

import tensorflow as tf

from text_generator import TextGenerator
from text_generator_model import TextGeneratorModel
from common import *

if (not 'MODEL_SAVEFILE' in os.environ or not 'VOCAB_SAVEFILE' in os.environ):
    print("MODEL_SAVEFILE or VOCAB_SAVEFILE environment variable are not set")
    exit(1)
savefile = os.environ['MODEL_SAVEFILE']
vocabfile = os.environ['VOCAB_SAVEFILE']

# Load the vocabulary
with open(vocabfile, "r") as file:
    vocabulary = json.load(file)

char_to_id = tf.keras.layers.StringLookup(vocabulary=vocabulary, mask_token=None)
id_to_char = tf.keras.layers.StringLookup(vocabulary=char_to_id.get_vocabulary(), invert=True, mask_token=None)

model = TextGeneratorModel(
    vocab_size=len(char_to_id.get_vocabulary()),
    batch_size=BATCH_SIZE,
    embedding_dim=EMBEDDING_UNITS,
    rnn_units=RNN_UNITS
)
# Load the model
model.load_weights(savefile)

generator = TextGenerator(model, id_to_char, char_to_id, 1.0)

sys.stderr.write("Generating. Type starting phrases to generate inputs.")

while True:
    phrase = input()

    text, time = generator.generate_message(phrase)
    print(text)
    print(f"{time} s")
    print("")
