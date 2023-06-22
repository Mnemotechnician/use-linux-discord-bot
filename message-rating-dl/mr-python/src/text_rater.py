import sys
import time
import tensorflow as tf

from common import *

class TextRater(tf.keras.Model):
    def __init__(self, model, id_to_char, char_to_id):
        super().__init__()

        self.model = model
        self.id_to_char = id_to_char
        self.char_to_id = char_to_id

    @tf.function
    def rate_text(self, inputs) -> tf.Tensor:
        """
        Rates strings.
        :param inputs: A rank-1 tensor containing a single string.
        :return: A rank-0 tensor containing the result.
        """
        # Convert strings to token IDs.
        input_chars = tf.strings.unicode_split(inputs, 'UTF-8')
        input_ids = self.char_to_id(input_chars).to_tensor()

        # Run the model.
        # Predicted_logits.shape is [batch, char, prediction_value]
        predictions = self.model(input_ids)

        return predictions[0][0]
