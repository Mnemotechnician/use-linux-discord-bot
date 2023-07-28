import tensorflow as tf

class TextRatingModel(tf.keras.Model):
    def __init__(self, batch_size, vocab_size, embedding_dim, rnn_units, dropout_rate=0.0):
        super().__init__(self)
        self.embedding = tf.keras.layers.Embedding(
            vocab_size,
            embedding_dim,
            mask_zero=True
        )
        self.rnn1 = tf.keras.layers.LSTM(
            rnn_units,
            dropout=dropout_rate,
            return_sequences=False,
            return_state=False
        )
        self.dense = tf.keras.layers.Dense(256, activation="swish")
        self.out = tf.keras.layers.Dense(1, activation="tanh")

        self.build(tf.TensorShape([batch_size, None]))

    @tf.function
    def call(self, inputs, training=False):
        x = inputs
        x = self.embedding(x, training=training)
        x = self.rnn1(x, training=training)
        # x = self.rnn2(x, training=training)
        # x = self.rnn3(x, training=training)
        x = self.dense(x)
        x = self.out(x)

        return x
