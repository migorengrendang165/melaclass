from flask import Flask, request, jsonify
import os
import logging
import tensorflow as tf
from tensorflow.keras.models import load_model
from tensorflow.keras.preprocessing.image import load_img, img_to_array
import numpy as np
import matplotlib.pyplot as plt
import matplotlib.image as img

app = Flask(__name__)

from tensorflow.keras.losses import SparseCategoricalCrossentropy

class CustomSparseCategoricalCrossentropy(SparseCategoricalCrossentropy):
    def __init__(self, reduction='auto', name='sparse_categorical_crossentropy', from_logits=False, ignore_class=None):
        super().__init__(reduction=reduction, name=name, from_logits=from_logits)

# Custom deserialization function
def custom_deserialize_loss(config, custom_objects=None):
    if config['class_name'] == 'SparseCategoricalCrossentropy':
        # Remove the unexpected 'fn' argument
        config['config'].pop('fn', None)
        return CustomSparseCategoricalCrossentropy(
            reduction=config['config'].get('reduction', 'auto'),
            name=config['config'].get('name', 'sparse_categorical_crossentropy'),
            from_logits=config['config'].get('from_logits', False),
            ignore_class=config['config'].get('ignore_class', None)
        )
    return deserialize(config, custom_objects)

# Custom objects mapping
custom_objects = {
    'SparseCategoricalCrossentropy': custom_deserialize_loss
}

# Load your pre-trained model
model = load_model('cnn_fc_model_full_04072000.h5', custom_objects=custom_objects)

# Define class names
class_names = ['melanoma', 'nevus']

# Function to preprocess the image
def preprocess_image(image_path):
    img = load_img(image_path, target_size=(256, 256))  # adjust target size according to your model
    img = img_to_array(img)
    img = np.expand_dims(img, axis=0)
    img = img / 255.0
    return img

# Endpoint to handle image upload
@app.route('/upload', methods=['POST'])
def upload_file():
    if 'image' not in request.files:
        return jsonify({'error': 'No image provided'}), 400

    image = request.files['image']

    if image.filename == '':
        return jsonify({'error': 'No image selected'}), 400

    if image:
        # Save the image to a folder named 'uploads'
        image.save(os.path.join('uploads', image.filename))
        
        # Preprocess the image
        # img = preprocess_image(os.path.join('uploads', image.filename))
        img_path = os.path.join('uploads', image.filename)
        img = tf.keras.utils.load_img(
            img_path, target_size=(256, 256)
        )
        img_array = tf.keras.utils.img_to_array(img)
        img_array = tf.expand_dims(img_array, 0)
        
        # Print the shape of the preprocessed image
        print(f"Preprocessed image shape: {img_array.shape}")
        
        # Make prediction
        #prediction = model.predict(img)
        prediction = model.predict(img_array)
        print(f"Prediction raw output: {prediction}")
        
        score = tf.nn.softmax(prediction[0])
        print("Sum of probabilities:", np.sum(score))
        confidence_threshold = 50
        score_list = score.numpy().tolist()

        #predicted_class_1 = np.argmax(prediction, axis=1)[0]
        #predicted_class_name_1 = class_names[predicted_class_1]
        #predicted_probabilities = prediction[0].tolist()
        
        #predicted_class_2 = np.argmax(predicted_probabilities)
        #predicted_class_name_2 = class_names[predicted_class_2]

        #percentages = [p * 100 for p in predicted_probabilities]
        
        #print(f"Predicted class index: {predicted_class_1}")
        #print(f"Predicted class name 1: {predicted_class_name_1}")
        #print(f"Predicted probabilities: {predicted_probabilities}")
        
        #print(f"Predicted class name 2: {predicted_class_name_2}")
        #print("Confidence scores:")
        #for i, percentage in enumerate(percentages):
        #    print(f"{class_names[i]}: {percentage:.2f}%")
            
        # Check if the highest confidence score meets the threshold
        if 100 * np.max(score) < confidence_threshold:
            print("The model is not confident enough to make a prediction for this image.")
        else:
            print(
            "This image most likely belongs to {} with a {:.2f} percent confidence."
            .format(class_names[np.argmax(score)], 100 * np.max(score))
            )

        print("\nConfidence scores for all classes:")
        for i, class_name in enumerate(class_names):
            print("This image belongs to {} with a {:.2f} percent confidence.".format(class_name, 100 * score_list[i]))
            
        # Preview the image
        img = tf.keras.utils.load_img(img_path, target_size=(180, 180))
        plt.imshow(img)
        plt.axis('off')
        plt.show()
        
        return jsonify({
            'message': 'Image uploaded successfully',
            'filename': image.filename,
            'predicted_class': class_names[np.argmax(score)],
            'predicted_probabilities': {class_name: 100 * score_list[i] for i, class_name in enumerate(class_names)}
        }), 200

if __name__ == '__main__':
    # Create the 'uploads' folder if it doesn't exist
    if not os.path.exists('uploads'):
        os.makedirs('uploads')

    # Run the Flask app on localhost:5000
    #app.run(debug=True)
    app.run(host='0.0.0.0', port=8000)
