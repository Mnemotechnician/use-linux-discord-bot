################################################################################################
# Run this script to train the model.                                                          #
# This script is used under the hood by TextGenerator.train()                                  #
################################################################################################
# Accept env variables:                                                                        #
# MODEL_SAVEFILE, VOCAB_SAVEFILE - file pathes                                                 #
# RESTORE_STATE - if set and not an empty state, will load the previous state before training. #
################################################################################################

import json
import os
import sys

import tensorflow as tf

from common import *
from text_generator_model import TextGeneratorModel

if (not 'MODEL_SAVEFILE' in os.environ or not 'VOCAB_SAVEFILE' in os.environ):
    print("MODEL_SAVEFILE or VOCAB_SAVEFILE environment variable are not set")
    exit(1)
savefile = os.environ['MODEL_SAVEFILE']
vocabfile = os.environ['VOCAB_SAVEFILE']
restore_state = bool(os.environ['RESTORE_STATE']) if 'RESTORE_STATE' in os.environ else False

# Read the dataset
text = sys.stdin.read()
# Tensor of lines of unicode characters
lines = tf.ragged.constant(list(map(
    lambda line: [char for char in list(line)],
    text.splitlines()
)))
# The first characters, a-z and A-Z, must come in pairs
vocabulary = list("aAbBcCdDeEfFgGhHiIjJkKlLmMnNoOpPqQrRsStTuUvVwWxXyYzZ")
# The rest of the vocabulary is sorted and inherited from the input
vocabulary.extend(
    list(filter(
        lambda char: not char in vocabulary, # Only retain non-letters
        sorted(set(text)) # Individual characters from the text
    ))
)

char_to_id = tf.keras.layers.StringLookup(vocabulary=vocabulary, mask_token=None)
id_to_char = tf.keras.layers.StringLookup(vocabulary=char_to_id.get_vocabulary(), invert=True, mask_token=None)

all_ids = char_to_id(lines)

dataset = (
    tf.data.Dataset.from_tensor_slices(all_ids)
        .map(lambda x: (x[:-1], x[1:]))
        .batch(BATCH_SIZE)
        .shuffle(1000, reshuffle_each_iteration=False)
        .prefetch(tf.data.experimental.AUTOTUNE))

model = TextGeneratorModel(
    vocab_size=len(char_to_id.get_vocabulary()),
    batch_size=BATCH_SIZE,
    embedding_dim=EMBEDDING_UNITS,
    rnn_units=RNN_UNITS,
    dropout_rate=DROPOUT_RATE
)

model.summary()

# Training the model
loss = tf.losses.SparseCategoricalCrossentropy(from_logits=True)
model.compile(optimizer='adam', loss=loss)

if (restore_state):
    model.load_weights(savefile)

history = model.fit(dataset, epochs=EPOCHS, callbacks=[])

# Save the model and vocabulary
model.save_weights(savefile)

with open(vocabfile, "w") as file:
    json.dump(char_to_id.get_vocabulary(), file)
