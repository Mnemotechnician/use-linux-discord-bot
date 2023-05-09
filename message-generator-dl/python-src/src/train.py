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


# Read the dataset
text = sys.stdin.read()
lines = text.splitlines()
vocabulary = sorted(set(text))

char_to_id = tf.keras.layers.StringLookup(vocabulary=vocabulary, mask_token=None)
id_to_char = tf.keras.layers.StringLookup(vocabulary=char_to_id.get_vocabulary(), invert=True, mask_token=None)

all_ids = char_to_id(tf.strings.unicode_split(lines, 'UTF-8'))
dataset = (
    tf.data.Dataset.from_tensor_slices(all_ids)
        .flat_map(lambda string:
            # String is a tensor. Batch it.
            tf.data.Dataset.from_tensor_slices(string)
                .batch(SEQUENCE_SIZE, drop_remainder=True)
                .map(lambda x: (x[:-1], x[1:])) # Split into tuples of (input, target)
        )
        .batch(BATCH_SIZE, drop_remainder=True)
        .shuffle(1000)
        .prefetch(tf.data.experimental.AUTOTUNE))


model = TextGeneratorModel(
    vocab_size=len(char_to_id.get_vocabulary()),
    batch_size=BATCH_SIZE,
    embedding_dim=EMBEDDING_UNITS,
    rnn_units=RNN_UNITS
)

model.summary()

# Training the model
loss = tf.losses.SparseCategoricalCrossentropy(from_logits=True)
model.compile(optimizer='adam', loss=loss)
history = model.fit(dataset, epochs=EPOCHS, callbacks=[])

# Save the model and vocabulary
model.save_weights(savefile)

with open(vocabfile, "w") as file:
    json.dump(char_to_id.get_vocabulary(), file)
