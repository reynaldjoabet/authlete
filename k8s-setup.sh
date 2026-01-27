minikube start --cpus 6 --memory 8g --insecure-registry "10.0.0.0/24"
minikube addons enable registry

docker run --rm -it --network=host alpine ash -c "apk add socat && socat TCP-LISTEN:5000,reuseaddr,fork TCP:$(minikube ip):5000"


