import tensorflow as tf

class TextGeneratorModel(tf.keras.Model):
    def __init__(self, batch_size, vocab_size, embedding_dim, rnn_units):
        super().__init__(self)
        self.embedding = tf.keras.layers.Embedding(vocab_size, embedding_dim)
        self.gru1 = tf.keras.layers.GRU(
            rnn_units,
            return_sequences=True,
            return_state=True
        )
        self.dropout = tf.keras.layers.Dropout(0.1)
        self.gru2 = tf.keras.layers.GRU(
            rnn_units // 4,
            return_sequences=True,
            return_state=True
        )
        self.dense = tf.keras.layers.Dense(vocab_size)

        self.build(tf.TensorShape([batch_size, None]))

    @tf.function
    def call(self, inputs, states: tuple=None, return_state=False, training=False):
        x = inputs
        x = self.embedding(x, training=training)

        # GRU 1
        if states is None:
            states = (self.gru1.get_initial_state(x), None)
        x, newState = self.gru1(x, initial_state=states[0], training=training)
        states = (newState, states[1])

        # Dropout
        x = self.dropout(x, training=training)

        # GRU 2
        if states[1] is None:
            states = (states[0], self.gru2.get_initial_state(x))
        x, newState = self.gru2(x, initial_state=states[1], training=training)
        states = (states[0], newState)

        # Output
        x = self.dense(x, training=training)

        if return_state:
            return x, states
        else:
            return x
