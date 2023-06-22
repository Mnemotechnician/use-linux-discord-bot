################################################################################################
# Run this script to train the model.                                                          #
# This script is used under the hood by MessageRating.train()                                  #
################################################################################################
# Accepted env variables:                                                                      #
# MODEL_SAVEFILE, VOCAB_SAVEFILE - file pathes                                                 #
# RESTORE_STATE - if set and not an empty state, will load the previous state before training. #
# EPOCHS - An integer, the number of training epochs.                                          #
################################################################################################

import json
import os
import sys

import tensorflow
import tensorflow as tf

from common import *
from text_rating_model import TextRatingModel

if (not 'MODEL_SAVEFILE' in os.environ or not 'VOCAB_SAVEFILE' in os.environ or not 'EPOCHS' in os.environ):
    print("MODEL_SAVEFILE, VOCAB_SAVEFILE or EPOCHS environment variable are not set")
    exit(1)
savefile = os.environ['MODEL_SAVEFILE']
vocabfile = os.environ['VOCAB_SAVEFILE']
epochs = int(os.environ['EPOCHS'])
restore_state = bool(os.environ['RESTORE_STATE']) if 'RESTORE_STATE' in os.environ else False

# Read the dataset
all_lines = list(filter(
    lambda line: len(line) > 2,
    sys.stdin.read().split("\t\t")
))

# for i in range(0, all_lines.__len__(), BATCH_SIZE):
#     lens = [list(line.split('\t')[0]).__len__() for line in all_lines[i:i+40]]
#     print(sum(lens)/lens.__len__(), lens)
# # exit(0)

# Build a vocabulary
vocabulary = set()
for line in all_lines:
    for char in line:
        vocabulary.add(char)
if MASK_TOKEN in vocabulary: vocabulary.remove(MASK_TOKEN)
if OOV_TOKEN in vocabulary: vocabulary.remove(OOV_TOKEN)

vocabulary = [MASK_TOKEN, OOV_TOKEN] + list(sorted(vocabulary))

print(vocabulary)
char_to_id = tf.keras.layers.StringLookup(vocabulary=vocabulary, mask_token=MASK_TOKEN, oov_token=OOV_TOKEN)
id_to_char = tf.keras.layers.StringLookup(vocabulary=char_to_id.get_vocabulary(), invert=True, mask_token=MASK_TOKEN, oov_token=OOV_TOKEN)

# The dataset
examples_x = char_to_id(tf.ragged.constant(list(map(
    lambda line: [char for char in line.split("\t")[0]],
    all_lines
))))
examples_y = list(map(
    lambda line: float(line.split("\t")[1]),
    all_lines
))

dataset = (
    tf.data.Dataset.from_tensor_slices((examples_x, examples_y))
        .batch(BATCH_SIZE)
        .map(lambda input, label: (input.to_tensor(), label)) # This is so dumb. Tensorflow is bullshit.
        .shuffle(1000, reshuffle_each_iteration=True)
        .prefetch(tf.data.experimental.AUTOTUNE))

model = TextRatingModel(
    vocab_size=len(char_to_id.get_vocabulary()),
    batch_size=BATCH_SIZE,
    embedding_dim=EMBEDDING_UNITS,
    rnn_units=RNN_UNITS,
    dropout_rate=DROPOUT_RATE
)

model.summary()

# Training the model
loss = tf.losses.MeanSquaredError()
optimizer = tf.optimizers.Adam(learning_rate=0.0003)
model.compile(optimizer=optimizer, loss=loss)

if (restore_state):
    model.load_weights(savefile)

history = model.fit(
    dataset,
    epochs=epochs,
    callbacks=[
        # tf.keras.callbacks.EarlyStopping(monitor='loss', patience=3, verbose=1, min_delta=0.001)
    ],
    use_multiprocessing=True
)

# Save the model and vocabulary
model.save_weights(savefile)

with open(vocabfile, "w") as file:
    json.dump(char_to_id.get_vocabulary(), file)
