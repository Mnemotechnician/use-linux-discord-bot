import tensorflow as tf
import numpy as np

class TextGeneratorModel(tf.keras.Model):
    def __init__(self, batch_size, vocab_size, embedding_dim, rnn_units, dropout_rate=0.0):
        super().__init__(self)

        self.vocab_size = vocab_size
        self.embedding_dim = embedding_dim

        self.embedding = tf.keras.layers.Embedding(
            vocab_size,
            embedding_dim,
            mask_zero=True
        )
        self.rnn1 = tf.keras.layers.LSTM(
            rnn_units // 2,
            dropout=dropout_rate,
            return_sequences=True,
            return_state=True
        )
        self.rnn2 = tf.keras.layers.LSTM(
            rnn_units,
            dropout=dropout_rate,
            return_sequences=True,
            return_state=True
        )
        self.dense = tf.keras.layers.Dense(vocab_size)

        self.build(tf.TensorShape([batch_size, None]))

    def load_embedding_layer(self, filepath: str, vocabulary: list):
        """
        Loads some pre-trained character embedding data.
        """
        embeddings_index = {}

        with open(filepath) as file:
            for line in file:
                if len(line) < 100:
                    continue
                values = line.split()
                char = values[0]
                embeddings = np.asarray(values[1:], dtype='float32')
                embeddings_index[char] = embeddings

        embedding_dim = len(embeddings_index['a'])

        if (embedding_dim != self.embedding_dim):
            raise Exception("Embedding dim mismatch")

        embedding_matrix = np.ndarray((len(vocabulary), embedding_dim))
        original_matrix = self.embedding.embeddings.numpy()

        i = 0
        for char in vocabulary:
            if (char in embeddings_index):
                embedding_matrix[i] = embeddings_index[char]
            else:
                embedding_matrix[i] = original_matrix[i]
            i += 1

        self.embedding.embeddings = embedding_matrix
        print(self.embedding.embeddings)

    @tf.function
    def call(self, inputs, states: tuple=None, return_states=False, training=False):
        x = self.embedding(inputs, training = training)

        if states is not None:
            initial_states_l1, initial_states_l2 = states

            x, state_h1, state_c1 = self.rnn1(x, initial_state=initial_states_l1, training=training)
            x, state_h2, state_c2 = self.rnn2(x, initial_state=initial_states_l2, training=training)
        else:
            x, state_h1, state_c1 = self.rnn1(x, training=training)
            x, state_h2, state_c2 = self.rnn2(x, training=training)

        x = self.dense(x)

        if return_states:
            return x, ([state_h1, state_c1], [state_h2, state_c2])
        else:
            return x
