########################################################################
# Run this script to begin rating texts.                               #
# This script is used under the hood by MessageRating.RatingProcess    #
########################################################################
# Accepted env variables:                                              #
# MODEL_SAVEFILE, VOCAB_SAVEFILE - file pathes                         #
########################################################################

import json
import os
import sys
import time

import tensorflow as tf

from text_rater import TextRater
from text_rating_model import TextRatingModel
from common import *

if (not 'MODEL_SAVEFILE' in os.environ or not 'VOCAB_SAVEFILE' in os.environ):
    print("MODEL_SAVEFILE or VOCAB_SAVEFILE environment variable are not set")
    exit(1)
savefile = os.environ['MODEL_SAVEFILE']
vocabfile = os.environ['VOCAB_SAVEFILE']

# Load the vocabulary
with open(vocabfile, "r") as file:
    vocabulary = json.load(file)

char_to_id = tf.keras.layers.StringLookup(vocabulary=vocabulary, mask_token=MASK_TOKEN, oov_token=OOV_TOKEN)
id_to_char = tf.keras.layers.StringLookup(vocabulary=char_to_id.get_vocabulary(), invert=True, mask_token=MASK_TOKEN, oov_token=OOV_TOKEN)

model = TextRatingModel(
    vocab_size=len(char_to_id.get_vocabulary()),
    batch_size=BATCH_SIZE,
    embedding_dim=EMBEDDING_UNITS,
    rnn_units=RNN_UNITS
)
# Load the model
model.load_weights(savefile)

rater = TextRater(model, id_to_char, char_to_id)

sys.stderr.write("Rating. Type messages to rate them. Delimit with tab followed by a newline.")

while True:
    text = ""
    while True:
        text += input()

        if text.endswith("\t"):
            text = text[:-1]
            break

    start_time = time.time()

    predictions = rater.rate_text(tf.constant([text]))

    print(predictions[0].numpy())
    print(f"{time.time() - start_time} s")
    print("   ")
