import sys
import time
import tensorflow as tf

from text_generator_model import TextGeneratorModel

from common import *

class TextGenerator(tf.keras.Model):
    def __init__(self, model, id_to_char, char_to_id, temperature=1.0):
        super().__init__()

        self.temperature: float = temperature
        self.model: TextGeneratorModel = model
        self.id_to_char = id_to_char
        self.char_to_id = char_to_id

        # Create a mask to prevent oov and mask tokens from being generated.
        skip_ids = self.char_to_id([MASK_TOKEN, OOV_TOKEN, MESSAGE_START])[:, None]
        sparse_mask = tf.SparseTensor(
            # Put a -inf at each bad index.
            values=[-float('inf')] * len(skip_ids),
            indices=skip_ids,
            # Match the shape to the vocabulary
            dense_shape=[len(char_to_id.get_vocabulary())])
        self.prediction_mask = tf.sparse.to_dense(sparse_mask)

    @tf.function
    def generate_one_step(self, inputs, states=None):
        """
        Perform a single step in the message generation.
        """
        # Convert strings to token IDs.
        input_chars = tf.strings.unicode_split(inputs, 'UTF-8')
        input_ids = self.char_to_id(input_chars).to_tensor()

        # Run the model.
        # Predicted_logits.shape is [batch, char, next_char_logits]
        predicted_logits, states = self.model(
            input_ids,
            states,
            True,
            False
        )

        # Only use the last prediction.
        predicted_logits = predicted_logits[:, -1, :]
        predicted_logits = predicted_logits / self.temperature
        # Apply the prediction mask: prevent "[UNK]" from being generated.
        predicted_logits = predicted_logits + self.prediction_mask

        # Sample the output logits to generate token IDs.
        predicted_ids = tf.random.categorical(predicted_logits, num_samples=1)
        predicted_ids = tf.squeeze(predicted_ids, axis=-1)

        # Convert from token ids to characters
        predicted_chars = self.id_to_char(predicted_ids)

        # Return the characters and model state.
        return predicted_chars, states

    def generate_message(self, starting_phrase: str) -> (str, float):
        """
        Generates a message.
        :return: A tuple of the generated message and the time in seconds it took to generate it.
        """
        start = time.time()
        states = self.model.create_initial_state(1)
        next_char: tf.Tensor = tf.constant([MESSAGE_START + starting_phrase], dtype=tf.string)
        result = ""
        length = 0

        while True:
            next_char, states = self.generate_one_step(next_char, states=states)

            string = next_char[0].numpy().decode('UTF-8')
            result += string
            length += len(string)

            if MESSAGE_TERMINATOR in string or length > 1000:
                break

        result = result.split(MESSAGE_TERMINATOR)[0].replace("\n", " ")
        end = time.time()

        return result, end - start
