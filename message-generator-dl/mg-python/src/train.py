################################################################################################
# Run this script to train the model.                                                          #
# This script is used under the hood by TextGenerator.train()                                  #
################################################################################################
# Accepted env variables:                                                                      #
# CHECKPOINT_DIR, CHECKPOINT - file pathes                                                     #
# PRETRAINED_EMBEDDING - file path                                                             #
# RESTORE_STATE - if set and not an empty state, will load the previous state before training. #
# EPOCHS - integer                                                                             #
################################################################################################
import datetime
import json
import os
import sys

import tensorflow as tf

from common import *
from text_generator_model import TextGeneratorModel

if ('CHECKPOINT_DIR' not in os.environ or 'EPOCHS' not in os.environ):
    print("CHECKPOINT_DIR or EPOCHS environment variables are not set")
    exit(1)
epochs = int(os.environ['EPOCHS'])
checkpoint_dir = os.environ['CHECKPOINT_DIR']
restore_state = bool(os.environ['RESTORE_STATE']) if 'RESTORE_STATE' in os.environ else False
pretrained_embedding = os.environ['PRETRAINED_EMBEDDING'] if 'PRETRAINED_EMBEDDING' in os.environ else None

# Read the dataset
text = sys.stdin.read()
# Tensor of lines of unicode characters
lines = tf.ragged.constant(list(map(
    lambda line: [char for char in list(line)],
    text.splitlines()
)))

if restore_state:
    loaded_checkpoint_dir = os.environ['CHECKPOINT']

    # Load the existing vocabulary
    with open(os.path.join(loaded_checkpoint_dir, "vocab.json"), "r") as file:
        vocabulary = json.load(file)
else:
    # Create a new vocabulary
    # The first characters, a-z and A-Z, come in pairs
    vocabulary = [MASK_TOKEN, OOV_TOKEN] + list("aAbBcCdDeEfFgGhHiIjJkKlLmMnNoOpPqQrRsStTuUvVwWxXyYzZ")
    # The rest of the vocabulary is sorted and inherited from the input
    vocabulary.extend(
        list(filter(
            lambda char: char not in vocabulary, # Only retain non-letters
            sorted(set(text)) # Individual characters from the text
        ))
    )

char_to_id = tf.keras.layers.StringLookup(vocabulary=vocabulary, mask_token=MASK_TOKEN, oov_token=OOV_TOKEN)
id_to_char = tf.keras.layers.StringLookup(vocabulary=char_to_id.get_vocabulary(), invert=True, mask_token=MASK_TOKEN, oov_token=OOV_TOKEN)

all_ids = char_to_id(lines)

dataset = (
    tf.data.Dataset.from_tensor_slices(all_ids)
        .map(lambda x: (x[:-1], x[1:]))
        .batch(BATCH_SIZE)
        .shuffle(1000, reshuffle_each_iteration=True)
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
optimizer = tf.optimizers.Adam(learning_rate=LEARNING_RATE)
model.compile(optimizer=optimizer, loss=loss)

if (restore_state):
    # noinspection PyUnboundLocalVariable
    model.load_weights(os.path.join(loaded_checkpoint_dir, "ckpt"))
elif pretrained_embedding is not None:
    model.load_embedding_layer(pretrained_embedding, char_to_id.get_vocabulary())

last_epoch = 0
def create_checkpoint_if_necessary(epoch=-1, logs=None):
    last_epoch = epoch
    if ((epoch + 1) % 5 == 0):
        timestamp = str(datetime.datetime.now()).split(".")[0].replace(":", ".")

        checkpoint = os.path.join(checkpoint_dir, f"ckpt_{timestamp}")
        if (logs is not None):
            checkpoint += " loss %.3f" % logs['loss']
        os.mkdir(checkpoint)

        print(f"\nBacking the current checkpoint up to {checkpoint}")
        model.save_weights(os.path.join(checkpoint, "ckpt"))

        with open(os.path.join(checkpoint, "vocab.json"), "w") as file:
            json.dump(char_to_id.get_vocabulary(), file)

history = model.fit(
    dataset,
    epochs=epochs,
    callbacks=[
        tf.keras.callbacks.EarlyStopping(monitor='loss', patience=3, verbose=1, min_delta=0.007),
        tf.keras.callbacks.LambdaCallback(on_epoch_end=create_checkpoint_if_necessary)
    ],
    use_multiprocessing=True
)

# The final checkpoint
if ((last_epoch + 1) % 5 != 0):
    create_checkpoint_if_necessary()
