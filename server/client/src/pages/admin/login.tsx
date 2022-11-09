import { FormEventHandler } from "react";
import AppBarLayout from "../../layout/AppBarLayout";
import BodyLayout from "../../layout/BodyLayout";
import { SubmitHandler, useForm } from 'react-hook-form';
import { TokenSchema } from "../../models/token";

type Inputs = {
  email: string;
  password: string;
}
const LoginForm: React.FC = () => {

  const {
    register,
    watch,
    handleSubmit,
    formState: {}
  } = useForm<Inputs>();
  // const handleSubmit: FormEventHandler<any> = (e) => {
  //   e.preventDefault();
  // };

  const onSubmit: SubmitHandler<Inputs> = async (e) => {
    const res = await fetch("/api/admin/accounts/login", {
      method: "POST",
      body: JSON.stringify({
        email: e.email,
        password: e.password
      }),
    });
    const result = await TokenSchema.safeParseAsync(await res.json());
    if (result.success) {
      console.log("成功", e)
    } else {
      console.log("失敗 status", res.status, result.error)
    }
  }
  return <div className="w-full h-full  flex justify-center items-center">
    <div className="p-8 drop-shadow-lg bg-white rounded-lg">
      <div className="pb-4">
        <h1 className="text-xl">Login</h1>
      </div>
      <form onSubmit={handleSubmit(onSubmit)}>
        <div className="pt-2 pb-2">
          <label htmlFor="email">Email</label>
          <input id="email" type="email" className="block w-96 border p-2 bg-gray-100 rounded-md" {...register("email")}/>
        </div>
        <div className="pt-2 pb-2">
          <label htmlFor="password">Password</label>
          <input id="password" type="password" className="block w-96 border p-2 bg-gray-100 rounded-md" {...register("password")}/>
        </div>
        <button type="submit" className="bg-sky-700 text-white p-2 rounded-lg">Login</button>
      </form>
    </div>
    
  </div>

}
const LoginPage: React.FC = () => {
    return (
        <BodyLayout topAppBar={
            <AppBarLayout>
                Login
            </AppBarLayout>
        }>
            <LoginForm />
        </BodyLayout>
    )
}



export default LoginPage;